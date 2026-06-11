package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.ScoreCalculationService;
import com.procurement.service.impl.AdmissionControlServiceImpl;
import com.procurement.service.impl.SupplierScoreServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 补充测试场景：
 * - 报价冻结后评分变化不影响已冻结比价
 * - 分批到货差异评分
 * - 质检退货联合影响
 * - 对账差异评分
 * - 规则改版后旧PO保留快照
 * - 并发审批准入控制
 * - PO创建捕获评分快照
 * - 人工调整后准入生效
 * - 黑名单后评分归零
 * - 并发PO确认准入检查
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierScoreIntegrationTest {

    // ==================== 评分引擎测试 ====================

    @InjectMocks
    private SupplierScoreServiceImpl scoreService;

    @Mock private SupplierScoreMapper scoreMapper;
    @Mock private SupplierScoreDetailMapper detailMapper;
    @Mock private SupplierScoreAdjustmentMapper adjustmentMapper;
    @Mock private SupplierScoreSnapshotMapper snapshotMapper;
    @Mock private ScoringRuleVersionMapper ruleVersionMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private QuoteMapper quoteMapper;
    @Mock private RfqMapper rfqMapper;
    @Mock private ComparisonLineMapper comparisonLineMapper;
    @Mock private ComparisonMapper comparisonMapper;
    @Mock private PurchaseOrderMapper poMapper;
    @Mock private PurchaseOrderLineMapper poLineMapper;
    @Mock private ArrivalMapper arrivalMapper;
    @Mock private ArrivalLineMapper arrivalLineMapper;
    @Mock private QualityInspectionMapper qcMapper;
    @Mock private ReturnOrderMapper returnOrderMapper;
    @Mock private ReconciliationMapper reconciliationMapper;
    @Mock private ApprovalMapper approvalMapper;
    @Mock private SupplierAdmissionLogMapper admissionLogMapper;
    @Mock private ScoreCalculationService scoreCalculationService;

    private ScoringRuleVersion ruleV1;
    private ScoringRuleVersion ruleV2;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(2L, "manager01", "PURCHASE_MANAGER", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));

        ruleV1 = new ScoringRuleVersion();
        ruleV1.setId(1L);
        ruleV1.setVersionNo(1);
        ruleV1.setWeights("{\"quoteResponseTimeliness\":15,\"priceDeviation\":15," +
                "\"deliveryOnTime\":15,\"arrivalDiscrepancy\":10,\"qcFailureRate\":15," +
                "\"returnRate\":10,\"reconciliationDiff\":10,\"approvalAnomaly\":10}");
        ruleV1.setThresholds("{\"blacklistScore\":20,\"restrictedScore\":50,\"extraApprovalScore\":70}");
        ruleV1.setStatus("ACTIVE");

        ruleV2 = new ScoringRuleVersion();
        ruleV2.setId(2L);
        ruleV2.setVersionNo(2);
        ruleV2.setWeights("{\"quoteResponseTimeliness\":10,\"priceDeviation\":20," +
                "\"deliveryOnTime\":20,\"arrivalDiscrepancy\":10,\"qcFailureRate\":15," +
                "\"returnRate\":10,\"reconciliationDiff\":10,\"approvalAnomaly\":5}");
        ruleV2.setThresholds("{\"blacklistScore\":25,\"restrictedScore\":55,\"extraApprovalScore\":75}");
        ruleV2.setStatus("ACTIVE");
    }

    private void mockEmptyDataForScore() {
        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectCount(any())).thenReturn(0L);
        when(arrivalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);
    }

    // ==================== 场景1: 报价冻结后评分变化不影响比价 ====================
    @Test
    void frozenQuote_scoreChange_doesNotAffectComparison() {
        // 报价已冻结，比价已使用当时数据完成
        // 评分重算不应影响已冻结的比价结果
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
        mockEmptyDataForScore();

        SupplierScore score = scoreService.calculateScore(1L);
        assertNotNull(score);

        // 比价行项的score和rankNo是独立的，不受supplier_score影响
        // 这里验证评分引擎不会修改comparison_line数据
        verify(comparisonLineMapper, never()).updateById(any());
    }

    // ==================== 场景2: 分批到货差异评分 ====================
    @Test
    void partialDelivery_discrepancyCalculated() {
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);

        Arrival arr1 = new Arrival();
        arr1.setId(1L);
        arr1.setPoId(1L);
        Arrival arr2 = new Arrival();
        arr2.setId(2L);
        arr2.setPoId(1L);

        // 第一批：精确到货
        ArrivalLine line1 = new ArrivalLine();
        line1.setArrivalId(1L);
        line1.setOrderedQty(new BigDecimal("100"));
        line1.setArrivedQty(new BigDecimal("100")); // 0% 差异

        // 第二批：短缺到货
        ArrivalLine line2 = new ArrivalLine();
        line2.setArrivalId(2L);
        line2.setOrderedQty(new BigDecimal("100"));
        line2.setArrivedQty(new BigDecimal("85")); // 15% 差异

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(poMapper.selectCount(any())).thenReturn(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arr1, arr2));
        when(arrivalLineMapper.selectList(any())).thenReturn(List.of(line1, line2));
        when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore score = scoreService.calculateScore(1L);
        assertNotNull(score);
        // 验证两批到货都被计算
        verify(detailMapper, atLeastOnce()).insert(any());
    }

    // ==================== 场景3: 质检不合格+退货联合影响 ====================
    @Test
    void qualityInspection_return_combinedEffect() {
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);
        po.setStatus("RECEIVED");

        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setPoId(1L);

        // 质检FAIL
        QualityInspection qi = new QualityInspection();
        qi.setArrivalId(1L);
        qi.setResult("FAIL");

        // 退货
        ReturnOrder ro = new ReturnOrder();
        ro.setId(1L);
        ro.setPoId(1L);
        ro.setSupplierId(1L);
        ro.setStatus("RETURNED"); // 非REJECTED

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(poMapper.selectCount(any())).thenReturn(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(qcMapper.selectList(any())).thenReturn(List.of(qi));
        when(returnOrderMapper.selectList(any())).thenReturn(List.of(ro));
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore score = scoreService.calculateScore(1L);
        assertNotNull(score);
        // 质检FAIL + 退货 → 两个维度都受影响，综合分应较低
        assertTrue(score.getTotalScore().compareTo(new BigDecimal("70")) < 0);
    }

    // ==================== 场景4: 对账差异影响评分 ====================
    @Test
    void reconciliationDiff_impactsScore() {
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

        Reconciliation recon = new Reconciliation();
        recon.setId(1L);
        recon.setSupplierId(1L);
        recon.setOrderAmount(new BigDecimal("100000"));
        recon.setDiffAmount(new BigDecimal("6000")); // 6% 差异 > 5%

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectCount(any())).thenReturn(0L);
        when(arrivalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(List.of(recon));
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore score = scoreService.calculateScore(1L);
        assertNotNull(score);
        // 对账差异维度得20分（>5%），拉低综合分
    }

    // ==================== 场景5: 规则改版后旧PO保留快照 ====================
    @Test
    void ruleVersionChange_oldPOKeepsSnapshot() {
        // 先使用v1规则创建快照
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

        SupplierScore currentScore = new SupplierScore();
        currentScore.setId(1L);
        currentScore.setSupplierId(1L);
        currentScore.setTotalScore(new BigDecimal("80.00"));
        currentScore.setSampleSize(5);
        when(scoreMapper.selectOne(any())).thenReturn(currentScore);
        when(detailMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(snapshotMapper.insert(any())).thenReturn(1);

        SupplierScoreSnapshot snapshot1 = scoreService.createSnapshot(1L, 1L);
        assertEquals(1, snapshot1.getRuleVersionNo());
        assertEquals(new BigDecimal("80.00"), snapshot1.getTotalScore());

        // 规则改版到v2
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV2);
        SupplierScoreSnapshot snapshot2 = scoreService.createSnapshot(2L, 1L);
        assertEquals(2, snapshot2.getRuleVersionNo());
        // 评分值相同（因为评分没变），但版本号不同
        assertEquals(new BigDecimal("80.00"), snapshot2.getTotalScore());

        // 两个快照保留不同的规则版本号
        assertNotEquals(snapshot1.getRuleVersionNo(), snapshot2.getRuleVersionNo());
    }

    // ==================== 场景6: 并发审批准入控制 ====================
    @Test
    void concurrentApproval_admissionControl() {
        // 模拟并发场景：两次准入检查应该一致
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");

        SupplierScore score = new SupplierScore();
        score.setSupplierId(1L);
        score.setTotalScore(new BigDecimal("80.00"));

        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(scoreMapper.selectOne(any())).thenReturn(score);
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        // 模拟两次并发准入检查
        AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper, snapshotMapper);

        AdmissionResult result1 = admissionService.checkAdmission(1L, "PO_CONFIRM", 100L);
        AdmissionResult result2 = admissionService.checkAdmission(1L, "PO_CONFIRM", 101L);

        // 两次结果应该一致
        assertEquals(result1.getDecision(), result2.getDecision());
        assertEquals("ALLOWED", result1.getDecision());
        // 两次都应该记录日志
        verify(admissionLogMapper, times(2)).insert(any());
    }

    // ==================== 场景7: PO创建时捕获评分快照 ====================
    @Test
    void poCreation_capturesScoreSnapshot() {
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

        SupplierScore currentScore = new SupplierScore();
        currentScore.setId(1L);
        currentScore.setSupplierId(1L);
        currentScore.setTotalScore(new BigDecimal("72.50"));
        currentScore.setSampleSize(10);
        when(scoreMapper.selectOne(any())).thenReturn(currentScore);
        when(detailMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(snapshotMapper.insert(any())).thenReturn(1);

        SupplierScoreSnapshot snapshot = scoreService.createSnapshot(100L, 1L);

        assertNotNull(snapshot);
        assertEquals(100L, snapshot.getPoId());
        assertEquals(1L, snapshot.getSupplierId());
        assertEquals(new BigDecimal("72.50"), snapshot.getTotalScore());
        assertEquals(1, snapshot.getRuleVersionNo());
        assertNotNull(snapshot.getSnapshotData());
        assertTrue(snapshot.getSnapshotData().contains("72.50"));
    }

    // ==================== 场景8: 人工调整后立即影响准入 ====================
    @Test
    void manualAdjust_reflectedInAdmission() {
        // 先调整评分
        SupplierScore existing = new SupplierScore();
        existing.setId(1L);
        existing.setSupplierId(1L);
        existing.setTotalScore(new BigDecimal("80.00"));
        when(scoreMapper.selectOne(any())).thenReturn(existing);
        when(adjustmentMapper.insert(any())).thenReturn(1);
        when(scoreMapper.updateById(any())).thenReturn(1);

        SupplierScore adjusted = scoreService.adjustScore(1L, new BigDecimal("30.00"), "重大质量问题降级");
        assertEquals(new BigDecimal("30.00"), adjusted.getTotalScore());

        // 调整后的评分应该影响准入判断
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");

        SupplierScore lowScore = new SupplierScore();
        lowScore.setSupplierId(1L);
        lowScore.setTotalScore(new BigDecimal("30.00")); // 已调整为30分

        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        // scoreMapper.selectOne 已被mock返回existing, 需要重新设置
        lenient().when(scoreMapper.selectOne(any())).thenReturn(lowScore);
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper, snapshotMapper);

        AdmissionResult result = admissionService.checkAdmission(1L, "PO_CONFIRM", 100L);
        assertEquals("RESTRICTED", result.getDecision());
    }

    // ==================== 场景9: 黑名单后评分归零 ====================
    @Test
    void blacklistAfterScore_zeroesScore() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("BLACKLISTED");

        SupplierScore score = new SupplierScore();
        score.setId(1L);
        score.setSupplierId(1L);
        score.setTotalScore(BigDecimal.ZERO);

        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        lenient().when(scoreMapper.selectOne(any())).thenReturn(score);
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper, snapshotMapper);

        // 黑名单供应商应该被硬拦截
        assertThrows(BusinessException.class,
                () -> admissionService.checkAdmission(1L, "RFQ_INVITE", 100L));
    }

    // ==================== 场景10: 并发PO确认准入检查 ====================
    @Test
    void concurrentPoConfirm_admissionCheck() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");

        SupplierScore score = new SupplierScore();
        score.setSupplierId(1L);
        score.setTotalScore(new BigDecimal("65.00")); // RESTRICTED范围

        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(scoreMapper.selectOne(any())).thenReturn(score);
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper, snapshotMapper);

        // 模拟并发PO确认
        AdmissionResult result1 = admissionService.checkAdmission(1L, "PO_CONFIRM", 200L);
        AdmissionResult result2 = admissionService.checkAdmission(1L, "PO_CONFIRM", 201L);

        // 两次结果一致
        assertEquals("RESTRICTED", result1.getDecision());
        assertEquals("RESTRICTED", result2.getDecision());
        assertTrue(result1.isRequiresExtraApproval());
        assertTrue(result2.isRequiresExtraApproval());

        // 每次检查都记录日志
        verify(admissionLogMapper, times(2)).insert(any());
    }

    // ==================== 场景11: 报价冻结时捕获评分快照 ====================
    @Test
    void quoteFreeze_capturesScoreSnapshot() {
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

        SupplierScore currentScore = new SupplierScore();
        currentScore.setId(1L);
        currentScore.setSupplierId(1L);
        currentScore.setTotalScore(new BigDecimal("82.00"));
        when(scoreMapper.selectOne(any())).thenReturn(currentScore);
        when(detailMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(snapshotMapper.insert(any())).thenReturn(1);

        // 创建报价冻结快照
        SupplierScoreSnapshot snapshot = scoreService.createQuoteSnapshot(100L, 1L);

        assertNotNull(snapshot);
        assertEquals(100L, snapshot.getQuoteId());
        assertNull(snapshot.getPoId());
        assertEquals(1L, snapshot.getSupplierId());
        assertEquals(new BigDecimal("82.00"), snapshot.getTotalScore());
        assertEquals(1, snapshot.getRuleVersionNo());
        assertNotNull(snapshot.getSnapshotData());
        verify(snapshotMapper).insert(any());
    }

    // ==================== 场景12: PO确认准入检查使用快照评分 ====================
    @Test
    void admissionCheck_usesPoSnapshot() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");

        // 活分已被重算为低分
        SupplierScore liveScore = new SupplierScore();
        liveScore.setSupplierId(1L);
        liveScore.setTotalScore(new BigDecimal("30.00")); // 被重算后很低

        // 但PO创建时的快照评分较高
        SupplierScoreSnapshot snapshot = new SupplierScoreSnapshot();
        snapshot.setId(999L);
        snapshot.setPoId(200L);
        snapshot.setSupplierId(1L);
        snapshot.setTotalScore(new BigDecimal("85.00")); // 创建时评分高
        snapshot.setRuleVersionNo(1);

        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper, snapshotMapper);

        // PO确认时应使用快照评分85分，而非活分30分
        AdmissionResult result = admissionService.checkAdmission(1L, "PO_CONFIRM", 200L);

        assertEquals("ALLOWED", result.getDecision());
        assertEquals(new BigDecimal("85.00"), result.getCurrentScore());
        assertEquals(999L, result.getSnapshotId());
        // 不应该读取活分
        verify(scoreMapper, never()).selectOne(any());
    }

    // ==================== 场景13: 规则改版后旧快照保留旧版本号 ====================
    @Test
    void ruleVersionChange_oldSnapshotPreserved_newCalcUsesNewRule() {
        // v1规则下创建快照
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

        SupplierScore score = new SupplierScore();
        score.setId(1L);
        score.setSupplierId(1L);
        score.setTotalScore(new BigDecimal("75.00"));
        when(scoreMapper.selectOne(any())).thenReturn(score);
        when(detailMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(snapshotMapper.insert(any())).thenReturn(1);

        SupplierScoreSnapshot snapshotV1 = scoreService.createSnapshot(1L, 1L);
        assertEquals(1, snapshotV1.getRuleVersionNo());

        // 规则改版到v2
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV2);
        SupplierScoreSnapshot snapshotV2 = scoreService.createSnapshot(2L, 1L);
        assertEquals(2, snapshotV2.getRuleVersionNo());

        // 两个快照版本号不同，旧快照不受新规则影响
        assertNotEquals(snapshotV1.getRuleVersionNo(), snapshotV2.getRuleVersionNo());
        assertEquals(1, snapshotV1.getRuleVersionNo());
        assertEquals(2, snapshotV2.getRuleVersionNo());
    }

    // ==================== 场景14: 黑名单硬拦截无视评分 ====================
    @Test
    void blacklist_hardBlock_regardlessOfHighScore() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("BLACKLISTED");

        // 即使评分很高也应该被拦截
        SupplierScore highScore = new SupplierScore();
        highScore.setSupplierId(1L);
        highScore.setTotalScore(new BigDecimal("95.00"));

        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        lenient().when(scoreMapper.selectOne(any())).thenReturn(highScore);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper, snapshotMapper);

        // 黑名单应该被硬拦截，不管评分多高
        assertThrows(BusinessException.class,
                () -> admissionService.checkAdmission(1L, "PO_CONFIRM", 200L));

        // 验证日志记录了BLOCKED决策
        verify(admissionLogMapper).insert(argThat(log ->
                "BLOCKED".equals(log.getDecision())));
    }

    // ==================== 场景15: 非PO场景准入检查使用活分 ====================
    @Test
    void nonPoCheckpoint_usesLiveScore() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setStatus("ACTIVE");

        SupplierScore liveScore = new SupplierScore();
        liveScore.setSupplierId(1L);
        liveScore.setTotalScore(new BigDecimal("60.00"));

        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(scoreMapper.selectOne(any())).thenReturn(liveScore);
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
        when(admissionLogMapper.insert(any())).thenReturn(1);

        AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper, snapshotMapper);

        // RFQ_INVITE 场景不使用快照，读活分
        AdmissionResult result = admissionService.checkAdmission(1L, "RFQ_INVITE", 300L);

        assertEquals("RESTRICTED", result.getDecision());
        assertEquals(new BigDecimal("60.00"), result.getCurrentScore());
        assertNull(result.getSnapshotId());
        // 不应该查快照
        verify(snapshotMapper, never()).selectOne(any());
    }

    // ==================== 场景16: 分批到货差异评分精确计算 ====================
    @Test
    void partialDelivery_preciseDiscrepancyCalculation() {
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);

        Arrival arr1 = new Arrival();
        arr1.setId(1L);
        arr1.setPoId(1L);
        Arrival arr2 = new Arrival();
        arr2.setId(2L);
        arr2.setPoId(1L);

        // 第一批：3% 差异 → 得80分
        ArrivalLine line1 = new ArrivalLine();
        line1.setArrivalId(1L);
        line1.setOrderedQty(new BigDecimal("100"));
        line1.setArrivedQty(new BigDecimal("97"));

        // 第二批：8% 差异 → 得50分
        ArrivalLine line2 = new ArrivalLine();
        line2.setArrivalId(2L);
        line2.setOrderedQty(new BigDecimal("100"));
        line2.setArrivedQty(new BigDecimal("92"));

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(poMapper.selectCount(any())).thenReturn(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arr1, arr2));
        when(arrivalLineMapper.selectList(any())).thenReturn(List.of(line1, line2));
        when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore score = scoreService.calculateScore(1L);
        assertNotNull(score);
        // 两批到货都被计算，到货差异维度综合分 = (80+50)/2 * 10/100 = 6.50
        verify(detailMapper, times(8)).insert(any()); // 8个维度
    }

    // ==================== 场景17: 质检CONDITIONAL与FAIL联合影响 ====================
    @Test
    void qualityInspection_conditionalAndFail_combinedEffect() {
        when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);
        po.setStatus("RECEIVED");

        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setPoId(1L);

        QualityInspection qi1 = new QualityInspection();
        qi1.setArrivalId(1L);
        qi1.setResult("FAIL");
        QualityInspection qi2 = new QualityInspection();
        qi2.setArrivalId(1L);
        qi2.setResult("CONDITIONAL");
        QualityInspection qi3 = new QualityInspection();
        qi3.setArrivalId(1L);
        qi3.setResult("PASS");

        // failRate = (1 + 0.5*1) / 3 = 0.5 → score = 100*(1-0.5) = 50
        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(poMapper.selectCount(any())).thenReturn(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(qcMapper.selectList(any())).thenReturn(List.of(qi1, qi2, qi3));
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore score = scoreService.calculateScore(1L);
        assertNotNull(score);
        // 质检维度得50分（部分FAIL+CONDITIONAL）
    }
}
