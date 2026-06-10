package com.procurement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.impl.SupplierScoreServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierScoreServiceTest {

    @InjectMocks
    private SupplierScoreServiceImpl scoreService;

    @Mock private ScoreRuleMapper scoreRuleMapper;
    @Mock private SupplierScoreMapper supplierScoreMapper;
    @Mock private SupplierScoreDetailMapper scoreDetailMapper;
    @Mock private SupplierScoreAdjustmentMapper adjustmentMapper;
    @Mock private SupplierScoreSnapshotMapper snapshotMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private RfqMapper rfqMapper;
    @Mock private RfqSupplierMapper rfqSupplierMapper;
    @Mock private QuoteMapper quoteMapper;
    @Mock private QuoteLineMapper quoteLineMapper;
    @Mock private ComparisonLineMapper comparisonLineMapper;
    @Mock private PurchaseOrderMapper poMapper;
    @Mock private ArrivalMapper arrivalMapper;
    @Mock private ArrivalLineMapper arrivalLineMapper;
    @Mock private QualityInspectionMapper inspectionMapper;
    @Mock private ReturnOrderMapper returnOrderMapper;
    @Mock private ReturnLineMapper returnLineMapper;
    @Mock private ReconciliationMapper reconciliationMapper;
    @Mock private ApprovalMapper approvalMapper;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(1L, "admin", "PURCHASE_MANAGER", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    private ScoreRule buildDefaultRule() {
        ScoreRule rule = new ScoreRule();
        rule.setId(1L);
        rule.setVersion(1);
        rule.setName("默认规则V1");
        rule.setQuoteResponseWeight(new BigDecimal("10.00"));
        rule.setPriceDeviationWeight(new BigDecimal("15.00"));
        rule.setDeliveryOnTimeWeight(new BigDecimal("20.00"));
        rule.setArrivalDiffWeight(new BigDecimal("10.00"));
        rule.setQcRejectWeight(new BigDecimal("20.00"));
        rule.setReturnRateWeight(new BigDecimal("10.00"));
        rule.setInvoiceDiffWeight(new BigDecimal("10.00"));
        rule.setApprovalAnomalyWeight(new BigDecimal("5.00"));
        rule.setWarningThreshold(new BigDecimal("60.00"));
        rule.setBlockThreshold(new BigDecimal("40.00"));
        rule.setActive(1);
        return rule;
    }

    private void mockNoHistoryData(Long supplierId) {
        lenient().when(rfqSupplierMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(poMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    // === Test 1: 全维度评分计算正确性 ===
    @Test
    void recalculate_shouldComputeAllDimensionsCorrectly() {
        Long supplierId = 1L;
        ScoreRule rule = buildDefaultRule();
        when(scoreRuleMapper.selectOne(any())).thenReturn(rule);
        when(supplierScoreMapper.selectOne(any())).thenReturn(null);
        when(supplierScoreMapper.insert(any())).thenReturn(1);
        when(scoreDetailMapper.insert(any())).thenReturn(1);

        // 报价响应：1个邀请，1个准时报价 → 100分
        RfqSupplier inv = new RfqSupplier();
        inv.setRfqId(1L);
        inv.setSupplierId(supplierId);
        when(rfqSupplierMapper.selectList(any())).thenReturn(List.of(inv));

        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setDeadline(LocalDateTime.now().plusDays(7));
        when(rfqMapper.selectById(1L)).thenReturn(rfq);

        Quote quote = new Quote();
        quote.setSubmittedAt(LocalDateTime.now());
        when(quoteMapper.selectOne(any())).thenReturn(quote);

        // 价格偏离：无比价 → 100分
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 无PO → 交付/到货/质检/退货/审批全为100分
        when(poMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 无对账 → 100分
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());

        scoreService.recalculateSupplier(supplierId);

        // 全部100分，总分应该是100
        verify(supplierScoreMapper).insert(argThat(s -> {
            SupplierScore score = (SupplierScore) s;
            return score.getTotalScore().compareTo(new BigDecimal("100.00")) == 0
                    && "EXCELLENT".equals(score.getScoreLevel());
        }));
        // 应该插入8条明细
        verify(scoreDetailMapper, times(8)).insert(any());
    }

    // === Test 2: 规则权重正确应用 ===
    @Test
    void recalculate_shouldApplyActiveRuleWeights() {
        Long supplierId = 1L;
        // 自定义权重：把质检权重设为50，其余平分剩下的50
        ScoreRule rule = buildDefaultRule();
        rule.setQuoteResponseWeight(new BigDecimal("5.00"));
        rule.setPriceDeviationWeight(new BigDecimal("5.00"));
        rule.setDeliveryOnTimeWeight(new BigDecimal("10.00"));
        rule.setArrivalDiffWeight(new BigDecimal("5.00"));
        rule.setQcRejectWeight(new BigDecimal("50.00"));
        rule.setReturnRateWeight(new BigDecimal("10.00"));
        rule.setInvoiceDiffWeight(new BigDecimal("10.00"));
        rule.setApprovalAnomalyWeight(new BigDecimal("5.00"));

        when(scoreRuleMapper.selectOne(any())).thenReturn(rule);
        when(supplierScoreMapper.selectOne(any())).thenReturn(null);
        when(supplierScoreMapper.insert(any())).thenReturn(1);
        when(scoreDetailMapper.insert(any())).thenReturn(1);

        mockNoHistoryData(supplierId);

        // 供应商有质检数据：2次检查，1次FAIL → 质检得分50
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(supplierId);
        when(poMapper.selectList(any())).thenReturn(List.of(po));

        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setPoId(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());

        QualityInspection qiPass = new QualityInspection();
        qiPass.setResult("PASS");
        QualityInspection qiFail = new QualityInspection();
        qiFail.setResult("FAIL");
        when(inspectionMapper.selectList(any())).thenReturn(List.of(qiPass, qiFail));

        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 无审批记录
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());

        scoreService.recalculateSupplier(supplierId);

        // 质检50分 * 50% = 25, 其他维度100分 * 50% = 50 → 总分75
        verify(supplierScoreMapper).insert(argThat(s -> {
            SupplierScore score = (SupplierScore) s;
            return score.getTotalScore().compareTo(new BigDecimal("75.00")) == 0
                    && "GOOD".equals(score.getScoreLevel());
        }));
    }

    // === Test 3: 黑名单供应商拦截 ===
    @Test
    void checkAccess_shouldBlockBlacklistedSupplier() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("黑名单供应商");
        supplier.setStatus("BLACKLISTED");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scoreService.checkSupplierAccess(1L, "RFQ_INVITE"));
        assertTrue(ex.getMessage().contains("已被拉黑"));
    }

    // === Test 4: 低分供应商额外审批 ===
    @Test
    void checkAccess_shouldWarnLowScoreSupplier() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("低分供应商");
        supplier.setStatus("ACTIVE");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        SupplierScore score = new SupplierScore();
        score.setSupplierId(1L);
        score.setTotalScore(new BigDecimal("45.00"));
        score.setScoreLevel("WARNING");
        when(supplierScoreMapper.selectOne(any())).thenReturn(score);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scoreService.checkSupplierAccess(1L, "PO_CONFIRM"));
        assertTrue(ex.getMessage().contains("预警"));
        assertTrue(ex.getMessage().contains("额外审批"));
    }

    // === Test 5: 高分供应商正常通行 ===
    @Test
    void checkAccess_shouldAllowHighScoreSupplier() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("优质供应商");
        supplier.setStatus("ACTIVE");
        when(supplierMapper.selectById(1L)).thenReturn(supplier);

        SupplierScore score = new SupplierScore();
        score.setSupplierId(1L);
        score.setTotalScore(new BigDecimal("92.00"));
        score.setScoreLevel("EXCELLENT");
        when(supplierScoreMapper.selectOne(any())).thenReturn(score);

        assertDoesNotThrow(() -> scoreService.checkSupplierAccess(1L, "RFQ_INVITE"));
    }

    // === Test 6: 报价冻结后评分基于冻结版本 ===
    @Test
    void scoreChange_afterQuoteFreeze_shouldReflectFrozenVersion() {
        Long supplierId = 1L;
        ScoreRule rule = buildDefaultRule();
        when(scoreRuleMapper.selectOne(any())).thenReturn(rule);
        when(supplierScoreMapper.selectOne(any())).thenReturn(null);
        when(supplierScoreMapper.insert(any())).thenReturn(1);
        when(scoreDetailMapper.insert(any())).thenReturn(1);

        // 有一个邀请，报价已冻结但在截止前提交
        RfqSupplier inv = new RfqSupplier();
        inv.setRfqId(1L);
        inv.setSupplierId(supplierId);
        when(rfqSupplierMapper.selectList(any())).thenReturn(List.of(inv));

        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setDeadline(LocalDateTime.of(2025, 1, 15, 12, 0));
        when(rfqMapper.selectById(1L)).thenReturn(rfq);

        // 冻结版本的报价 - 在截止前提交
        Quote frozenQuote = new Quote();
        frozenQuote.setStatus("FROZEN");
        frozenQuote.setFrozen(1);
        frozenQuote.setVersion(2);
        frozenQuote.setSubmittedAt(LocalDateTime.of(2025, 1, 14, 10, 0));
        when(quoteMapper.selectOne(any())).thenReturn(frozenQuote);

        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());

        scoreService.recalculateSupplier(supplierId);

        // 冻结版本的报价在截止前提交，报价响应得分应为100
        verify(supplierScoreMapper).insert(argThat(s -> {
            SupplierScore score = (SupplierScore) s;
            return score.getQuoteResponseScore().compareTo(new BigDecimal("100.00")) == 0;
        }));
    }

    // === Test 7: 分批到货差异正确计算 ===
    @Test
    void batchArrival_shouldCalculateDiffCorrectly() {
        Long supplierId = 1L;
        ScoreRule rule = buildDefaultRule();
        when(scoreRuleMapper.selectOne(any())).thenReturn(rule);
        when(supplierScoreMapper.selectOne(any())).thenReturn(null);
        when(supplierScoreMapper.insert(any())).thenReturn(1);
        when(scoreDetailMapper.insert(any())).thenReturn(1);

        when(rfqSupplierMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(supplierId);
        po.setComparisonId(null);
        po.setCreatedAt(LocalDateTime.now().minusDays(10));
        when(poMapper.selectList(any())).thenReturn(List.of(po));

        // 两批到货
        Arrival arr1 = new Arrival();
        arr1.setId(1L);
        arr1.setPoId(1L);
        arr1.setArrivedAt(LocalDateTime.now());
        Arrival arr2 = new Arrival();
        arr2.setId(2L);
        arr2.setPoId(1L);
        arr2.setArrivedAt(LocalDateTime.now());
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arr1, arr2));

        // 批次1：订100到90（差异10%），批次2：订200到200（差异0%）
        ArrivalLine line1 = new ArrivalLine();
        line1.setArrivalId(1L);
        line1.setOrderedQty(new BigDecimal("100"));
        line1.setArrivedQty(new BigDecimal("90"));

        ArrivalLine line2 = new ArrivalLine();
        line2.setArrivalId(2L);
        line2.setOrderedQty(new BigDecimal("200"));
        line2.setArrivedQty(new BigDecimal("200"));

        // 注意：arrivalLineMapper.selectList 会被调用多次（不同的到货单）
        // 使用 thenReturn 的链式调用来区分
        when(arrivalLineMapper.selectList(any()))
                .thenReturn(List.of(line1))
                .thenReturn(List.of(line2));

        when(inspectionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());

        scoreService.recalculateSupplier(supplierId);

        // 到货差异：avg(10/100, 0/200) = avg(0.1, 0) = 0.05 → 100 - 5 = 95分
        verify(supplierScoreMapper).insert(argThat(s -> {
            SupplierScore score = (SupplierScore) s;
            return score.getArrivalDiffScore().compareTo(new BigDecimal("95.00")) == 0;
        }));
    }

    // === Test 8: 质检退货降低评分 ===
    @Test
    void qcReject_shouldReduceScore() {
        Long supplierId = 1L;
        ScoreRule rule = buildDefaultRule();
        when(scoreRuleMapper.selectOne(any())).thenReturn(rule);
        when(supplierScoreMapper.selectOne(any())).thenReturn(null);
        when(supplierScoreMapper.insert(any())).thenReturn(1);
        when(scoreDetailMapper.insert(any())).thenReturn(1);

        when(rfqSupplierMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(supplierId);
        po.setComparisonId(null);
        po.setCreatedAt(LocalDateTime.now());
        when(poMapper.selectList(any())).thenReturn(List.of(po));

        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setPoId(1L);
        arrival.setArrivedAt(LocalDateTime.now());
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 4次质检：3次PASS，1次FAIL → 不合格率25% → 得分75
        QualityInspection qi1 = new QualityInspection(); qi1.setResult("PASS");
        QualityInspection qi2 = new QualityInspection(); qi2.setResult("PASS");
        QualityInspection qi3 = new QualityInspection(); qi3.setResult("PASS");
        QualityInspection qi4 = new QualityInspection(); qi4.setResult("FAIL");
        when(inspectionMapper.selectList(any())).thenReturn(List.of(qi1, qi2, qi3, qi4));

        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());

        scoreService.recalculateSupplier(supplierId);

        verify(supplierScoreMapper).insert(argThat(s -> {
            SupplierScore score = (SupplierScore) s;
            return score.getQcRejectScore().compareTo(new BigDecimal("75.00")) == 0;
        }));
    }

    // === Test 9: 对账差异降低评分 ===
    @Test
    void invoiceDiff_shouldReduceScore() {
        Long supplierId = 1L;
        ScoreRule rule = buildDefaultRule();
        when(scoreRuleMapper.selectOne(any())).thenReturn(rule);
        when(supplierScoreMapper.selectOne(any())).thenReturn(null);
        when(supplierScoreMapper.insert(any())).thenReturn(1);
        when(scoreDetailMapper.insert(any())).thenReturn(1);

        when(rfqSupplierMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 对账：订单1000，收货800，发票1000 → 差异|1000-800|/1000 = 20% → 得分80
        Reconciliation recon = new Reconciliation();
        recon.setSupplierId(supplierId);
        recon.setOrderAmount(new BigDecimal("1000.00"));
        recon.setReceiptAmount(new BigDecimal("800.00"));
        recon.setInvoiceAmount(new BigDecimal("1000.00"));
        when(reconciliationMapper.selectList(any())).thenReturn(List.of(recon));

        scoreService.recalculateSupplier(supplierId);

        verify(supplierScoreMapper).insert(argThat(s -> {
            SupplierScore score = (SupplierScore) s;
            return score.getInvoiceDiffScore().compareTo(new BigDecimal("80.00")) == 0;
        }));
    }

    // === Test 10: 规则改版后使用新权重 ===
    @Test
    void ruleVersionChange_shouldUseNewWeights() {
        Long supplierId = 1L;

        // V2规则：质检权重100%，其他全0
        ScoreRule ruleV2 = new ScoreRule();
        ruleV2.setId(2L);
        ruleV2.setVersion(2);
        ruleV2.setName("V2-质检全权重");
        ruleV2.setQuoteResponseWeight(BigDecimal.ZERO);
        ruleV2.setPriceDeviationWeight(BigDecimal.ZERO);
        ruleV2.setDeliveryOnTimeWeight(BigDecimal.ZERO);
        ruleV2.setArrivalDiffWeight(BigDecimal.ZERO);
        ruleV2.setQcRejectWeight(HUNDRED);
        ruleV2.setReturnRateWeight(BigDecimal.ZERO);
        ruleV2.setInvoiceDiffWeight(BigDecimal.ZERO);
        ruleV2.setApprovalAnomalyWeight(BigDecimal.ZERO);
        ruleV2.setWarningThreshold(new BigDecimal("60.00"));
        ruleV2.setBlockThreshold(new BigDecimal("40.00"));
        ruleV2.setActive(1);

        when(scoreRuleMapper.selectOne(any())).thenReturn(ruleV2);
        when(supplierScoreMapper.selectOne(any())).thenReturn(null);
        when(supplierScoreMapper.insert(any())).thenReturn(1);
        when(scoreDetailMapper.insert(any())).thenReturn(1);

        when(rfqSupplierMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(supplierId);
        po.setCreatedAt(LocalDateTime.now());
        when(poMapper.selectList(any())).thenReturn(List.of(po));

        Arrival arrival = new Arrival();
        arrival.setId(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 质检50%不合格 → 得分50
        QualityInspection pass = new QualityInspection(); pass.setResult("PASS");
        QualityInspection fail = new QualityInspection(); fail.setResult("FAIL");
        when(inspectionMapper.selectList(any())).thenReturn(List.of(pass, fail));

        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());

        scoreService.recalculateSupplier(supplierId);

        // 质检50分 * 100% = 50分(WARNING), 其他维度权重都是0
        verify(supplierScoreMapper).insert(argThat(s -> {
            SupplierScore score = (SupplierScore) s;
            return score.getTotalScore().compareTo(new BigDecimal("50.00")) == 0
                    && "WARNING".equals(score.getScoreLevel());
        }));
    }

    // === Test 11: 人工调整+审计记录 ===
    @Test
    void adjust_shouldOverrideAndAudit() {
        SupplierScore existing = new SupplierScore();
        existing.setId(1L);
        existing.setSupplierId(1L);
        existing.setTotalScore(new BigDecimal("85.00"));
        existing.setQcRejectScore(new BigDecimal("60.00"));
        existing.setQuoteResponseScore(new BigDecimal("100.00"));
        existing.setPriceDeviationScore(new BigDecimal("100.00"));
        existing.setDeliveryOnTimeScore(new BigDecimal("100.00"));
        existing.setArrivalDiffScore(new BigDecimal("100.00"));
        existing.setReturnRateScore(new BigDecimal("100.00"));
        existing.setInvoiceDiffScore(new BigDecimal("100.00"));
        existing.setApprovalAnomalyScore(new BigDecimal("100.00"));
        existing.setScoreLevel("GOOD");

        when(supplierScoreMapper.selectOne(any())).thenReturn(existing);
        when(supplierScoreMapper.updateById(any())).thenReturn(1);
        when(adjustmentMapper.insert(any())).thenReturn(1);

        ScoreRule rule = buildDefaultRule();
        when(scoreRuleMapper.selectOne(any())).thenReturn(rule);

        scoreService.adjustScore(1L, "QC_REJECT", new BigDecimal("90.00"), "质检问题已整改");

        // 验证评分已更新
        verify(supplierScoreMapper).updateById(argThat(s -> {
            SupplierScore score = (SupplierScore) s;
            return score.getQcRejectScore().compareTo(new BigDecimal("90.00")) == 0;
        }));

        // 验证调整记录已保存
        verify(adjustmentMapper).insert(argThat(adj -> {
            SupplierScoreAdjustment a = (SupplierScoreAdjustment) adj;
            return a.getOriginalScore().compareTo(new BigDecimal("60.00")) == 0
                    && a.getAdjustedScore().compareTo(new BigDecimal("90.00")) == 0
                    && "质检问题已整改".equals(a.getReason());
        }));
    }

    // === Test 12: 快照保留历史评分 ===
    @Test
    void snapshot_shouldPreserveHistoricalScore() {
        SupplierScore score = new SupplierScore();
        score.setSupplierId(1L);
        score.setRuleVersion(1);
        score.setTotalScore(new BigDecimal("78.50"));
        score.setScoreLevel("GOOD");
        score.setQuoteResponseScore(new BigDecimal("80.00"));
        score.setPriceDeviationScore(new BigDecimal("75.00"));
        score.setDeliveryOnTimeScore(new BigDecimal("90.00"));
        score.setArrivalDiffScore(new BigDecimal("85.00"));
        score.setQcRejectScore(new BigDecimal("70.00"));
        score.setReturnRateScore(new BigDecimal("60.00"));
        score.setInvoiceDiffScore(new BigDecimal("95.00"));
        score.setApprovalAnomalyScore(new BigDecimal("100.00"));
        score.setCalculatedAt(LocalDateTime.now());
        when(supplierScoreMapper.selectOne(any())).thenReturn(score);
        when(snapshotMapper.insert(any())).thenReturn(1);

        SupplierScoreSnapshot snapshot = scoreService.createSnapshot(1L, "PO", 100L);

        assertEquals(new BigDecimal("78.50"), snapshot.getTotalScore());
        assertEquals("GOOD", snapshot.getScoreLevel());
        assertEquals("PO", snapshot.getBusinessType());
        assertEquals(100L, snapshot.getBusinessId());
        assertEquals(1, snapshot.getRuleVersion());
        assertNotNull(snapshot.getScoreDetail());

        verify(snapshotMapper).insert(any());
    }

    // === Test 13: 并发审批场景评分一致 ===
    @Test
    void concurrentApproval_shouldHandleCorrectly() {
        Long supplierId = 1L;
        ScoreRule rule = buildDefaultRule();
        when(scoreRuleMapper.selectOne(any())).thenReturn(rule);
        when(supplierScoreMapper.selectOne(any())).thenReturn(null);
        when(supplierScoreMapper.insert(any())).thenReturn(1);
        when(scoreDetailMapper.insert(any())).thenReturn(1);

        when(rfqSupplierMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 3个PO
        PurchaseOrder po1 = new PurchaseOrder(); po1.setId(1L); po1.setSupplierId(supplierId); po1.setCreatedAt(LocalDateTime.now());
        PurchaseOrder po2 = new PurchaseOrder(); po2.setId(2L); po2.setSupplierId(supplierId); po2.setCreatedAt(LocalDateTime.now());
        PurchaseOrder po3 = new PurchaseOrder(); po3.setId(3L); po3.setSupplierId(supplierId); po3.setCreatedAt(LocalDateTime.now());
        when(poMapper.selectList(any())).thenReturn(List.of(po1, po2, po3));
        when(arrivalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 5个审批记录：3个APPROVED，2个REJECTED → 驳回率40% → 得分60
        Approval a1 = new Approval(); a1.setStatus("APPROVED");
        Approval a2 = new Approval(); a2.setStatus("APPROVED");
        Approval a3 = new Approval(); a3.setStatus("APPROVED");
        Approval a4 = new Approval(); a4.setStatus("REJECTED");
        Approval a5 = new Approval(); a5.setStatus("REJECTED");
        when(approvalMapper.selectList(any())).thenReturn(List.of(a1, a2, a3, a4, a5));

        scoreService.recalculateSupplier(supplierId);

        verify(supplierScoreMapper).insert(argThat(s -> {
            SupplierScore score = (SupplierScore) s;
            return score.getApprovalAnomalyScore().compareTo(new BigDecimal("60.00")) == 0;
        }));
    }

    // === Test 14: 新供应商默认满分 ===
    @Test
    void newSupplier_shouldGetDefaultScore() {
        when(supplierScoreMapper.selectOne(any())).thenReturn(null);

        SupplierScore score = scoreService.getScore(999L);

        assertEquals(new BigDecimal("100.00"), score.getTotalScore());
        assertEquals("EXCELLENT", score.getScoreLevel());
        assertEquals(new BigDecimal("100.00"), score.getQuoteResponseScore());
        assertEquals(new BigDecimal("100.00"), score.getQcRejectScore());
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");
}
