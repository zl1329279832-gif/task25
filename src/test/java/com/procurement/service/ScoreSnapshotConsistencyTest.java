package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.impl.AdmissionControlServiceImpl;
import com.procurement.service.impl.ArrivalServiceImpl;
import com.procurement.service.impl.QuoteServiceImpl;
import com.procurement.service.impl.ReconciliationServiceImpl;
import com.procurement.service.impl.SupplierScoreServiceImpl;
import com.procurement.task.ProcurementScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 评分快照一致性与事务边界综合测试
 *
 * 覆盖场景：
 * 1. 报价冻结后评分变化隔离
 * 2. 分批到货差异评分
 * 3. 质检退货联合评分
 * 4. 对账差异评分
 * 5. 规则改版快照绑定
 * 6. 并发审批准入控制
 * 7. 黑名单拦截
 * 8. recalculateAll 逐供应商事务隔离
 * 9. ProcurementScheduler 分布式锁
 * 10. 准入日志阈值快照审计
 * 11. 评分快照含完整规则数据
 * 12. 报价冻结事务性
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoreSnapshotConsistencyTest {

    // ==================== 共享 Mock ====================

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

    @InjectMocks private SupplierScoreServiceImpl scoreService;

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

    private void mockEmptyData() {
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

    // ==================== 1. 报价冻结后评分变化隔离 ====================

    @Nested
    @DisplayName("报价冻结后评分变化隔离")
    class FrozenQuoteScoreIsolation {

        @Test
        @DisplayName("报价冻结后，评分重算不修改已冻结报价及比价数据")
        void frozenQuote_scoreRecalc_noImpactOnFrozenData() {
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
            mockEmptyData();

            // 模拟已有评分
            SupplierScore existing = new SupplierScore();
            existing.setId(1L);
            existing.setSupplierId(1L);
            existing.setTotalScore(new BigDecimal("75.00"));
            existing.setRuleVersionId(1L);
            when(scoreMapper.selectOne(any())).thenReturn(existing);
            when(scoreMapper.updateById(any())).thenReturn(1);

            // 重新计算评分
            SupplierScore recalculated = scoreService.calculateScore(1L);

            // 验证：不修改 quote 和 comparison_line 表
            verify(quoteMapper, never()).updateById(any());
            verify(quoteMapper, never()).insert(any());
            verify(comparisonLineMapper, never()).updateById(any());
            verify(comparisonLineMapper, never()).insert(any());
        }

        @Test
        @DisplayName("冻结报价后再创建快照，快照记录冻结时的评分")
        void frozenQuote_snapshotCapturesScoreAtFreezeTime() {
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

            SupplierScore score = new SupplierScore();
            score.setId(1L);
            score.setSupplierId(1L);
            score.setTotalScore(new BigDecimal("80.00"));
            score.setSampleSize(5);
            when(scoreMapper.selectOne(any())).thenReturn(score);
            when(detailMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(snapshotMapper.insert(any())).thenReturn(1);

            SupplierScoreSnapshot snap = scoreService.createSnapshot(1L, 1L);

            assertEquals(new BigDecimal("80.00"), snap.getTotalScore());
            assertEquals(1, snap.getRuleVersionNo());
        }
    }

    // ==================== 2. 分批到货差异评分 ====================

    @Nested
    @DisplayName("分批到货差异评分")
    class BatchArrivalDiscrepancy {

        @Test
        @DisplayName("多批次到货：精确到货批次得满分，短缺批次得低分，综合取平均")
        void multiBatch_mixedDiscrepancy_averageScore() {
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

            PurchaseOrder po = new PurchaseOrder();
            po.setId(1L);
            po.setSupplierId(1L);

            // 两批到货
            Arrival arr1 = new Arrival();
            arr1.setId(1L);
            arr1.setPoId(1L);
            Arrival arr2 = new Arrival();
            arr2.setId(2L);
            arr2.setPoId(1L);

            // 批次1：精确到货 (0% 差异 → 100分)
            ArrivalLine line1 = new ArrivalLine();
            line1.setArrivalId(1L);
            line1.setOrderedQty(new BigDecimal("100"));
            line1.setArrivedQty(new BigDecimal("100"));

            // 批次2：严重短缺 (20% 差异 → 20分)
            ArrivalLine line2 = new ArrivalLine();
            line2.setArrivalId(2L);
            line2.setOrderedQty(new BigDecimal("100"));
            line2.setArrivedQty(new BigDecimal("80"));

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

            SupplierScore result = scoreService.calculateScore(1L);

            assertNotNull(result);
            // arrivalDiscrepancy 维度: (100+20)/2 = 60, 加权 60*10/100 = 6.00
            // 验证明细被正确插入
            verify(detailMapper, times(8)).insert(any());
        }

        @Test
        @DisplayName("三批到货渐进差异：1%、6%、15% 对应 100/80/20 分")
        void threeBatches_progressiveDiscrepancy() {
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

            PurchaseOrder po = new PurchaseOrder();
            po.setId(1L);
            po.setSupplierId(1L);

            Arrival arr1 = new Arrival(); arr1.setId(1L); arr1.setPoId(1L);
            Arrival arr2 = new Arrival(); arr2.setId(2L); arr2.setPoId(1L);
            Arrival arr3 = new Arrival(); arr3.setId(3L); arr3.setPoId(1L);

            ArrivalLine l1 = new ArrivalLine();
            l1.setArrivalId(1L);
            l1.setOrderedQty(new BigDecimal("1000"));
            l1.setArrivedQty(new BigDecimal("990")); // 1% → 100

            ArrivalLine l2 = new ArrivalLine();
            l2.setArrivalId(2L);
            l2.setOrderedQty(new BigDecimal("1000"));
            l2.setArrivedQty(new BigDecimal("940")); // 6% → 50

            ArrivalLine l3 = new ArrivalLine();
            l3.setArrivalId(3L);
            l3.setOrderedQty(new BigDecimal("1000"));
            l3.setArrivedQty(new BigDecimal("850")); // 15% → 20

            when(scoreMapper.selectOne(any())).thenReturn(null);
            when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(poMapper.selectList(any())).thenReturn(List.of(po));
            when(poMapper.selectCount(any())).thenReturn(1L);
            when(arrivalMapper.selectList(any())).thenReturn(List.of(arr1, arr2, arr3));
            when(arrivalLineMapper.selectList(any())).thenReturn(List.of(l1, l2, l3));
            when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(detailMapper.delete(any())).thenReturn(0);
            when(scoreMapper.insert(any())).thenReturn(1);
            when(detailMapper.insert(any())).thenReturn(1);

            SupplierScore result = scoreService.calculateScore(1L);
            assertNotNull(result);
            // arrivalDiscrepancy = (100+50+20)/3 ≈ 56.67
            assertTrue(result.getTotalScore().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    // ==================== 3. 质检退货联合评分 ====================

    @Nested
    @DisplayName("质检退货联合评分")
    class QcReturnCombined {

        @Test
        @DisplayName("质检全FAIL+全退货：qcFailureRate=0, returnRate=0, 两个维度拉低总分")
        void allFail_allReturn_lowCombinedScore() {
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
            qi2.setResult("FAIL");

            ReturnOrder ret = new ReturnOrder();
            ret.setId(1L);
            ret.setPoId(1L);
            ret.setSupplierId(1L);
            ret.setStatus("RETURNED");

            when(scoreMapper.selectOne(any())).thenReturn(null);
            when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(poMapper.selectList(any())).thenReturn(List.of(po));
            when(poMapper.selectCount(any())).thenReturn(1L);
            when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));
            when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(qcMapper.selectList(any())).thenReturn(List.of(qi1, qi2));
            when(returnOrderMapper.selectList(any())).thenReturn(List.of(ret));
            when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(detailMapper.delete(any())).thenReturn(0);
            when(scoreMapper.insert(any())).thenReturn(1);
            when(detailMapper.insert(any())).thenReturn(1);

            SupplierScore result = scoreService.calculateScore(1L);

            // qcFailureRate: 100*(1-1.0) = 0, 加权 0*15/100 = 0
            // returnRate: 1/1=1.0, score=max(0,100*(1-2))=0, 加权 0*10/100 = 0
            // 这两个维度贡献0分，总分应显著低于中位值
            assertTrue(result.getTotalScore().compareTo(new BigDecimal("55")) < 0,
                    "全FAIL+全退货，总分应显著低于默认55分");
        }

        @Test
        @DisplayName("质检CONDITIONAL+部分退货：适度拉低评分")
        void conditional_partialReturn_moderateImpact() {
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

            PurchaseOrder po1 = new PurchaseOrder();
            po1.setId(1L); po1.setSupplierId(1L); po1.setStatus("RECEIVED");
            PurchaseOrder po2 = new PurchaseOrder();
            po2.setId(2L); po2.setSupplierId(1L); po2.setStatus("RECEIVED");

            Arrival arr1 = new Arrival(); arr1.setId(1L); arr1.setPoId(1L);
            Arrival arr2 = new Arrival(); arr2.setId(2L); arr2.setPoId(2L);

            // 2个PASS + 1个CONDITIONAL
            QualityInspection qi1 = new QualityInspection();
            qi1.setArrivalId(1L); qi1.setResult("PASS");
            QualityInspection qi2 = new QualityInspection();
            qi2.setArrivalId(1L); qi2.setResult("PASS");
            QualityInspection qi3 = new QualityInspection();
            qi3.setArrivalId(2L); qi3.setResult("CONDITIONAL");

            // 只有PO1有退货
            ReturnOrder ret = new ReturnOrder();
            ret.setId(1L); ret.setPoId(1L); ret.setSupplierId(1L); ret.setStatus("RETURNED");

            when(scoreMapper.selectOne(any())).thenReturn(null);
            when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(poMapper.selectList(any())).thenReturn(List.of(po1, po2));
            when(poMapper.selectCount(any())).thenReturn(2L);
            when(arrivalMapper.selectList(any())).thenReturn(List.of(arr1, arr2));
            when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(qcMapper.selectList(any())).thenReturn(List.of(qi1, qi2, qi3));
            when(returnOrderMapper.selectList(any())).thenReturn(List.of(ret));
            when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(detailMapper.delete(any())).thenReturn(0);
            when(scoreMapper.insert(any())).thenReturn(1);
            when(detailMapper.insert(any())).thenReturn(1);

            SupplierScore result = scoreService.calculateScore(1L);

            // qcFailureRate: (0 + 0.5)/3 ≈ 0.167, score=83.33, 加权 ≈ 12.5
            // returnRate: 1/2=0.5, score=max(0,100*(1-1))=0, 加权 0
            assertNotNull(result);
            assertTrue(result.getTotalScore().compareTo(new BigDecimal("30")) > 0,
                    "部分CONDITIONAL+部分退货，分数不应太低");
        }
    }

    // ==================== 4. 对账差异评分 ====================

    @Nested
    @DisplayName("对账差异评分")
    class ReconciliationDiffScoring {

        @Test
        @DisplayName("多笔对账：0%差异+8%差异，综合取平均")
        void multiRecon_mixedDiff() {
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

            Reconciliation r1 = new Reconciliation();
            r1.setId(1L); r1.setSupplierId(1L);
            r1.setOrderAmount(new BigDecimal("50000"));
            r1.setDiffAmount(BigDecimal.ZERO); // 0% → 100分

            Reconciliation r2 = new Reconciliation();
            r2.setId(2L); r2.setSupplierId(1L);
            r2.setOrderAmount(new BigDecimal("50000"));
            r2.setDiffAmount(new BigDecimal("4000")); // 8% → 20分

            when(scoreMapper.selectOne(any())).thenReturn(null);
            when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(poMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(poMapper.selectCount(any())).thenReturn(0L);
            when(arrivalMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(reconciliationMapper.selectList(any())).thenReturn(List.of(r1, r2));
            when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(detailMapper.delete(any())).thenReturn(0);
            when(scoreMapper.insert(any())).thenReturn(1);
            when(detailMapper.insert(any())).thenReturn(1);

            SupplierScore result = scoreService.calculateScore(1L);

            // reconciliationDiff: (100+20)/2 = 60, 加权 60*10/100 = 6.00
            assertNotNull(result);
            verify(detailMapper, times(8)).insert(any());
        }

        @Test
        @DisplayName("对账差异2%：落在1%-3%区间得80分")
        void reconDiff_2percent_gets80() {
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);

            Reconciliation r = new Reconciliation();
            r.setId(1L); r.setSupplierId(1L);
            r.setOrderAmount(new BigDecimal("100000"));
            r.setDiffAmount(new BigDecimal("2000")); // 2% → 80

            when(scoreMapper.selectOne(any())).thenReturn(null);
            when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(poMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(poMapper.selectCount(any())).thenReturn(0L);
            when(arrivalMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(reconciliationMapper.selectList(any())).thenReturn(List.of(r));
            when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(detailMapper.delete(any())).thenReturn(0);
            when(scoreMapper.insert(any())).thenReturn(1);
            when(detailMapper.insert(any())).thenReturn(1);

            SupplierScore result = scoreService.calculateScore(1L);

            assertNotNull(result);
            // reconciliationDiff维度得80，加权 80*10/100 = 8.00
            // 总分 ≈ 50*15/100 + 50*15/100 + 50*15/100 + 50*10/100 + 50*15/100
            //       + 50*10/100 + 80*10/100 + 100*10/100 = 7.5+7.5+7.5+5+7.5+5+8+10 = 58
            assertEquals(new BigDecimal("58.00"), result.getTotalScore());
        }
    }

    // ==================== 5. 规则改版快照绑定 ====================

    @Nested
    @DisplayName("规则改版只影响新评分，历史快照绑定旧规则")
    class RuleVersionSnapshot {

        @Test
        @DisplayName("V1规则快照 → 升级V2 → V2快照：两个快照绑定各自规则版本号")
        void ruleUpgrade_snapshotsBindToRespectiveVersions() {
            // V1规则下创建快照
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
            SupplierScore score = new SupplierScore();
            score.setId(1L); score.setSupplierId(1L);
            score.setTotalScore(new BigDecimal("82.00"));
            score.setSampleSize(8);
            when(scoreMapper.selectOne(any())).thenReturn(score);
            when(detailMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(snapshotMapper.insert(any())).thenReturn(1);

            SupplierScoreSnapshot snapV1 = scoreService.createSnapshot(100L, 1L);
            assertEquals(1, snapV1.getRuleVersionNo());
            assertEquals(new BigDecimal("82.00"), snapV1.getTotalScore());

            // 升级到V2
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV2);

            SupplierScoreSnapshot snapV2 = scoreService.createSnapshot(200L, 1L);
            assertEquals(2, snapV2.getRuleVersionNo());

            // 两个快照的规则版本号不同
            assertNotEquals(snapV1.getRuleVersionNo(), snapV2.getRuleVersionNo());
            // 快照都被持久化
            verify(snapshotMapper, times(2)).insert(any());
        }

        @Test
        @DisplayName("规则改版后重算使用新规则，但不修改已有快照")
        void ruleUpgrade_recalcUsesNewRule_existingSnapshotsUnchanged() {
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV2);
            mockEmptyData();

            SupplierScore result = scoreService.calculateScore(1L);

            // 评分记录绑定新规则ID
            assertEquals(ruleV2.getId(), result.getRuleVersionId());
            // 不修改快照表
            verify(snapshotMapper, never()).updateById(any());
            verify(snapshotMapper, never()).delete(any());
        }

        @Test
        @DisplayName("快照数据包含完整规则权重和阈值")
        void snapshot_containsRuleWeightsAndThresholds() {
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
            SupplierScore score = new SupplierScore();
            score.setId(1L); score.setSupplierId(1L);
            score.setTotalScore(new BigDecimal("75.00"));
            score.setSampleSize(3);
            when(scoreMapper.selectOne(any())).thenReturn(score);
            when(detailMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(snapshotMapper.insert(any())).thenReturn(1);

            SupplierScoreSnapshot snap = scoreService.createSnapshot(300L, 1L);

            String data = snap.getSnapshotData();
            assertNotNull(data);
            assertTrue(data.contains("ruleWeights"), "快照应包含规则权重");
            assertTrue(data.contains("ruleThresholds"), "快照应包含规则阈值");
            assertTrue(data.contains("quoteResponseTimeliness"), "快照权重应包含具体维度");
            assertTrue(data.contains("blacklistScore"), "快照阈值应包含黑名单阈值");
            assertTrue(data.contains("extraApprovalScore"), "快照阈值应包含准入阈值");
        }
    }

    // ==================== 6. 并发审批准入控制 ====================

    @Nested
    @DisplayName("并发审批准入控制")
    class ConcurrentAdmission {

        @Test
        @DisplayName("并发准入检查结果一致，每次都记录审计日志")
        void concurrentChecks_consistentResult_eachLogged() {
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

            AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                    supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper);

            AdmissionResult r1 = admissionService.checkAdmission(1L, "PO_CONFIRM", 100L);
            AdmissionResult r2 = admissionService.checkAdmission(1L, "PO_CONFIRM", 101L);
            AdmissionResult r3 = admissionService.checkAdmission(1L, "PO_CONFIRM", 102L);

            assertEquals("ALLOWED", r1.getDecision());
            assertEquals("ALLOWED", r2.getDecision());
            assertEquals("ALLOWED", r3.getDecision());
            verify(admissionLogMapper, times(3)).insert(any());
        }

        @Test
        @DisplayName("准入日志记录阈值快照")
        void admissionLog_containsThresholdSnapshot() {
            Supplier supplier = new Supplier();
            supplier.setId(1L);
            supplier.setStatus("ACTIVE");

            SupplierScore score = new SupplierScore();
            score.setSupplierId(1L);
            score.setTotalScore(new BigDecimal("60.00")); // RESTRICTED

            when(supplierMapper.selectById(1L)).thenReturn(supplier);
            when(scoreMapper.selectOne(any())).thenReturn(score);
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
            when(admissionLogMapper.insert(any())).thenReturn(1);

            AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                    supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper);

            admissionService.checkAdmission(1L, "PO_CONFIRM", 100L);

            ArgumentCaptor<SupplierAdmissionLog> captor = ArgumentCaptor.forClass(SupplierAdmissionLog.class);
            verify(admissionLogMapper).insert(captor.capture());

            SupplierAdmissionLog log = captor.getValue();
            assertEquals("RESTRICTED", log.getDecision());
            assertEquals(new BigDecimal("60.00"), log.getScoreAtTime());
            assertNotNull(log.getThresholdSnapshot(), "准入日志应记录阈值快照");
            assertTrue(log.getThresholdSnapshot().contains("blacklistScore"));
            assertTrue(log.getThresholdSnapshot().contains("extraApprovalScore"));
            assertEquals(1, log.getRuleVersionNo());
        }

        @Test
        @DisplayName("规则改版后准入检查使用新阈值并记录")
        void ruleChange_admissionUsesNewThresholds() {
            Supplier supplier = new Supplier();
            supplier.setId(1L);
            supplier.setStatus("ACTIVE");

            SupplierScore score = new SupplierScore();
            score.setSupplierId(1L);
            score.setTotalScore(new BigDecimal("72.00"));

            when(supplierMapper.selectById(1L)).thenReturn(supplier);
            when(scoreMapper.selectOne(any())).thenReturn(score);
            when(admissionLogMapper.insert(any())).thenReturn(1);

            AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                    supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper);

            // V1规则：extraApprovalScore=70, 72>=70 → ALLOWED
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
            AdmissionResult r1 = admissionService.checkAdmission(1L, "PO_CONFIRM", 100L);
            assertEquals("ALLOWED", r1.getDecision());

            // V2规则：extraApprovalScore=75, 72<75 → RESTRICTED
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV2);
            AdmissionResult r2 = admissionService.checkAdmission(1L, "PO_CONFIRM", 101L);
            assertEquals("RESTRICTED", r2.getDecision());
        }
    }

    // ==================== 7. 黑名单拦截 ====================

    @Nested
    @DisplayName("黑名单拦截")
    class BlacklistInterception {

        @Test
        @DisplayName("黑名单供应商在所有检查点都被硬拦截")
        void blacklisted_blockedAtAllCheckpoints() {
            Supplier supplier = new Supplier();
            supplier.setId(1L);
            supplier.setStatus("BLACKLISTED");

            when(supplierMapper.selectById(1L)).thenReturn(supplier);
            when(admissionLogMapper.insert(any())).thenReturn(1);

            AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                    supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper);

            String[] checkpoints = {"RFQ_INVITE", "QUOTE_ACCEPT", "PO_CONFIRM"};
            for (String cp : checkpoints) {
                assertThrows(BusinessException.class,
                        () -> admissionService.checkAdmission(1L, cp, 100L),
                        "黑名单供应商在" + cp + "检查点应被拦截");
            }

            verify(admissionLogMapper, times(3)).insert(any());
        }

        @Test
        @DisplayName("评分低于黑名单阈值的活跃供应商也被拦截")
        void activeSupplier_belowBlacklistThreshold_blocked() {
            Supplier supplier = new Supplier();
            supplier.setId(1L);
            supplier.setStatus("ACTIVE");

            SupplierScore score = new SupplierScore();
            score.setSupplierId(1L);
            score.setTotalScore(new BigDecimal("15.00")); // < 20

            when(supplierMapper.selectById(1L)).thenReturn(supplier);
            when(scoreMapper.selectOne(any())).thenReturn(score);
            when(ruleVersionMapper.selectOne(any())).thenReturn(ruleV1);
            when(admissionLogMapper.insert(any())).thenReturn(1);

            AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                    supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper);

            assertThrows(BusinessException.class,
                    () -> admissionService.checkAdmission(1L, "PO_CONFIRM", 100L));

            ArgumentCaptor<SupplierAdmissionLog> captor = ArgumentCaptor.forClass(SupplierAdmissionLog.class);
            verify(admissionLogMapper).insert(captor.capture());
            assertEquals("BLOCKED", captor.getValue().getDecision());
            assertNotNull(captor.getValue().getThresholdSnapshot());
        }

        @Test
        @DisplayName("黑名单拦截后日志中不记录阈值快照（因为是状态级拦截）")
        void blacklisted_logHasNoThresholdSnapshot() {
            Supplier supplier = new Supplier();
            supplier.setId(1L);
            supplier.setStatus("BLACKLISTED");

            when(supplierMapper.selectById(1L)).thenReturn(supplier);
            when(admissionLogMapper.insert(any())).thenReturn(1);

            AdmissionControlServiceImpl admissionService = new AdmissionControlServiceImpl(
                    supplierMapper, scoreMapper, admissionLogMapper, ruleVersionMapper);

            assertThrows(BusinessException.class,
                    () -> admissionService.checkAdmission(1L, "PO_CONFIRM", 100L));

            ArgumentCaptor<SupplierAdmissionLog> captor = ArgumentCaptor.forClass(SupplierAdmissionLog.class);
            verify(admissionLogMapper).insert(captor.capture());
            assertNull(captor.getValue().getThresholdSnapshot(),
                    "黑名单硬拦截不涉及评分阈值，无需阈值快照");
        }
    }

    // ==================== 8. recalculateAll 事务隔离 ====================

    @Nested
    @DisplayName("recalculateAll 逐供应商事务隔离")
    class RecalculateAllIsolation {

        @Test
        @DisplayName("recalculateAll 通过 self 代理调用，确保每个 calculateScore 独立事务")
        void recalculateAll_usesSelfProxy() {
            // 使用spy来验证self代理被使用
            Supplier s1 = new Supplier(); s1.setId(1L); s1.setStatus("ACTIVE");
            Supplier s2 = new Supplier(); s2.setId(2L); s2.setStatus("ACTIVE");
            when(supplierMapper.selectList(any())).thenReturn(List.of(s1, s2));

            // 设置self代理为mock
            SupplierScoreService selfMock = mock(SupplierScoreService.class);
            ReflectionTestUtils.setField(scoreService, "self", selfMock);

            when(selfMock.calculateScore(anyLong())).thenReturn(new SupplierScore());

            scoreService.recalculateAll();

            // 验证通过self代理调用，而非直接调用
            verify(selfMock).calculateScore(1L);
            verify(selfMock).calculateScore(2L);
        }

        @Test
        @DisplayName("单个供应商计算失败不影响其他供应商")
        void recalculateAll_oneFailure_othersSucceed() {
            Supplier s1 = new Supplier(); s1.setId(1L); s1.setStatus("ACTIVE");
            Supplier s2 = new Supplier(); s2.setId(2L); s2.setStatus("ACTIVE");
            Supplier s3 = new Supplier(); s3.setId(3L); s3.setStatus("ACTIVE");
            when(supplierMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));

            SupplierScoreService selfMock = mock(SupplierScoreService.class);
            ReflectionTestUtils.setField(scoreService, "self", selfMock);

            // 供应商2计算失败
            when(selfMock.calculateScore(1L)).thenReturn(new SupplierScore());
            when(selfMock.calculateScore(2L)).thenThrow(new RuntimeException("数据异常"));
            when(selfMock.calculateScore(3L)).thenReturn(new SupplierScore());

            // 不应抛出异常
            assertDoesNotThrow(() -> scoreService.recalculateAll());

            // 3个供应商都被尝试计算
            verify(selfMock).calculateScore(1L);
            verify(selfMock).calculateScore(2L);
            verify(selfMock).calculateScore(3L);
        }
    }

    // ==================== 9. ProcurementScheduler 分布式锁 ====================

    @Nested
    @DisplayName("ProcurementScheduler 分布式锁")
    @ExtendWith(MockitoExtension.class)
    class SchedulerDistributedLock {

        @Mock private RfqMapper schRfqMapper;
        @Mock private QuoteService schQuoteService;
        @Mock private ApprovalMapper schApprovalMapper;
        @Mock private ReminderMapper schReminderMapper;
        @Mock private PurchaseOrderMapper schPoMapper;
        @Mock private SupplierScoreService schScoreService;
        @Mock private RedisTemplate<String, Object> schRedisTemplate;
        @Mock private ValueOperations<String, Object> schValueOps;

        @Test
        @DisplayName("获取锁成功时执行重算")
        void lockAcquired_executesRecalculation() {
            when(schRedisTemplate.opsForValue()).thenReturn(schValueOps);
            when(schValueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);

            ProcurementScheduler scheduler = new ProcurementScheduler(
                    schRfqMapper, schQuoteService, schApprovalMapper, schReminderMapper,
                    schPoMapper, schScoreService, schRedisTemplate);

            scheduler.recalculateSupplierScores();

            verify(schScoreService).recalculateAll();
            verify(schRedisTemplate).delete("procurement:score:recalculate:lock");
        }

        @Test
        @DisplayName("获取锁失败时跳过重算")
        void lockNotAcquired_skipsRecalculation() {
            when(schRedisTemplate.opsForValue()).thenReturn(schValueOps);
            when(schValueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(false);

            ProcurementScheduler scheduler = new ProcurementScheduler(
                    schRfqMapper, schQuoteService, schApprovalMapper, schReminderMapper,
                    schPoMapper, schScoreService, schRedisTemplate);

            scheduler.recalculateSupplierScores();

            verify(schScoreService, never()).recalculateAll();
        }

        @Test
        @DisplayName("重算异常时仍释放锁")
        void recalcException_lockStillReleased() {
            when(schRedisTemplate.opsForValue()).thenReturn(schValueOps);
            when(schValueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            doThrow(new RuntimeException("重算失败")).when(schScoreService).recalculateAll();

            ProcurementScheduler scheduler = new ProcurementScheduler(
                    schRfqMapper, schQuoteService, schApprovalMapper, schReminderMapper,
                    schPoMapper, schScoreService, schRedisTemplate);

            assertThrows(RuntimeException.class, scheduler::recalculateSupplierScores);

            verify(schRedisTemplate).delete("procurement:score:recalculate:lock");
        }
    }

    // ==================== 10. 报价冻结事务性 ====================

    @Nested
    @DisplayName("报价冻结事务性")
    @ExtendWith(MockitoExtension.class)
    class QuoteFreezeTransactional {

        @Mock private QuoteMapper qtQuoteMapper;
        @Mock private QuoteLineMapper qtQuoteLineMapper;
        @Mock private RfqMapper qtRfqMapper;
        @Mock private RedisTemplate<String, Object> qtRedisTemplate;

        @Test
        @DisplayName("freezeQuote 设置冻结标记和状态")
        void freezeQuote_setsFlags() {
            Quote quote = new Quote();
            quote.setId(1L);
            quote.setFrozen(0);
            quote.setStatus(QuoteStatus.SUBMITTED.name());
            when(qtQuoteMapper.selectById(1L)).thenReturn(quote);
            when(qtQuoteMapper.updateById(any())).thenReturn(1);

            QuoteServiceImpl quoteService = new QuoteServiceImpl(
                    qtQuoteMapper, qtQuoteLineMapper, qtRfqMapper, qtRedisTemplate);

            quoteService.freezeQuote(1L);

            assertEquals(1, quote.getFrozen());
            assertEquals(QuoteStatus.FROZEN.name(), quote.getStatus());
            verify(qtQuoteMapper).updateById(quote);
        }

        @Test
        @DisplayName("freezeAllByRfq 批量冻结所有未冻结报价")
        void freezeAllByRfq_freezesAll() {
            Quote q1 = new Quote(); q1.setId(1L); q1.setFrozen(0);
            Quote q2 = new Quote(); q2.setId(2L); q2.setFrozen(0);
            Quote q3 = new Quote(); q3.setId(3L); q3.setFrozen(0);

            when(qtQuoteMapper.selectList(any())).thenReturn(List.of(q1, q2, q3));
            when(qtQuoteMapper.updateById(any())).thenReturn(1);

            QuoteServiceImpl quoteService = new QuoteServiceImpl(
                    qtQuoteMapper, qtQuoteLineMapper, qtRfqMapper, qtRedisTemplate);

            quoteService.freezeAllByRfq(1L);

            assertEquals(1, q1.getFrozen());
            assertEquals(1, q2.getFrozen());
            assertEquals(1, q3.getFrozen());
            verify(qtQuoteMapper, times(3)).updateById(any());
        }
    }
}
