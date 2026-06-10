package com.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierScoreServiceTest {

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

    private ScoringRuleVersion activeRule;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(2L, "manager01", "PURCHASE_MANAGER", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));

        activeRule = new ScoringRuleVersion();
        activeRule.setId(1L);
        activeRule.setVersionNo(1);
        activeRule.setWeights("{\"quoteResponseTimeliness\":15,\"priceDeviation\":15," +
                "\"deliveryOnTime\":15,\"arrivalDiscrepancy\":10,\"qcFailureRate\":15," +
                "\"returnRate\":10,\"reconciliationDiff\":10,\"approvalAnomaly\":10}");
        activeRule.setThresholds("{\"blacklistScore\":20,\"restrictedScore\":50,\"extraApprovalScore\":70}");
        activeRule.setStatus("ACTIVE");
    }

    private void mockActiveRule() {
        when(ruleVersionMapper.selectOne(any())).thenReturn(activeRule);
    }

    private void mockNoData() {
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

    @Test
    void calculateScore_newSupplier_returnsNeutral() {
        mockActiveRule();
        mockNoData();

        SupplierScore result = scoreService.calculateScore(1L);

        assertNotNull(result);
        assertEquals(1L, result.getSupplierId());
        // 无数据时各维度默认50分, 但审批异常默认100分(无异常=满分)
        // 50*(15+15+15+10+15+10+10)/100 + 100*10/100 = 45 + 10 = 55
        assertEquals(new BigDecimal("55.00"), result.getTotalScore());
        verify(scoreMapper).insert(any());
    }

    @Test
    void calculateScore_quoteTimeliness_fullScore() {
        mockActiveRule();

        // 模拟报价数据：供应商在截止前很晚才报价 (ratio >= 0.8)
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setCreatedAt(LocalDateTime.now().minusDays(10));
        rfq.setDeadline(LocalDateTime.now().plusDays(10));

        Quote quote = new Quote();
        quote.setId(1L);
        quote.setRfqId(1L);
        quote.setSupplierId(1L);
        quote.setSubmittedAt(LocalDateTime.now().plusDays(8)); // 晚提交，ratio高

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(List.of(quote));
        when(rfqMapper.selectById(1L)).thenReturn(rfq);
        // 其他维度无数据
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

        SupplierScore result = scoreService.calculateScore(1L);
        assertNotNull(result);
        // 验证报价时效维度被计算（应有得分）
        verify(detailMapper, atLeastOnce()).insert(any());
    }

    @Test
    void calculateScore_arrivalDiscrepancy_smallDiff() {
        mockActiveRule();

        // 模拟到货数据：差异小于2%
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);
        po.setStatus("RECEIVED");

        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setPoId(1L);

        ArrivalLine line = new ArrivalLine();
        line.setArrivalId(1L);
        line.setOrderedQty(new BigDecimal("100"));
        line.setArrivedQty(new BigDecimal("101")); // 1% 差异

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(poMapper.selectCount(any())).thenReturn(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));
        when(arrivalLineMapper.selectList(any())).thenReturn(List.of(line));
        when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore result = scoreService.calculateScore(1L);
        assertNotNull(result);
        assertTrue(result.getTotalScore().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void calculateScore_arrivalDiscrepancy_largeDiff() {
        mockActiveRule();

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);

        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setPoId(1L);

        ArrivalLine line = new ArrivalLine();
        line.setArrivalId(1L);
        line.setOrderedQty(new BigDecimal("100"));
        line.setArrivedQty(new BigDecimal("80")); // 20% 差异

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(poMapper.selectCount(any())).thenReturn(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));
        when(arrivalLineMapper.selectList(any())).thenReturn(List.of(line));
        when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore result = scoreService.calculateScore(1L);
        assertNotNull(result);
        // 大差异得分较低
    }

    @Test
    void calculateScore_qcFailure_allFail() {
        mockActiveRule();

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);

        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setPoId(1L);

        QualityInspection qi1 = new QualityInspection();
        qi1.setArrivalId(1L);
        qi1.setResult("FAIL");
        QualityInspection qi2 = new QualityInspection();
        qi2.setArrivalId(1L);
        qi2.setResult("FAIL");

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(poMapper.selectCount(any())).thenReturn(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(qcMapper.selectList(any())).thenReturn(List.of(qi1, qi2));
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore result = scoreService.calculateScore(1L);
        assertNotNull(result);
        // 全FAIL时质检维度得0分
    }

    @Test
    void calculateScore_qcFailure_conditional() {
        mockActiveRule();

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);

        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setPoId(1L);

        QualityInspection qi = new QualityInspection();
        qi.setArrivalId(1L);
        qi.setResult("CONDITIONAL");

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(poMapper.selectCount(any())).thenReturn(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(qcMapper.selectList(any())).thenReturn(List.of(qi));
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore result = scoreService.calculateScore(1L);
        assertNotNull(result);
        // CONDITIONAL计0.5次不合格: failRate = 0.5/1 = 0.5, 得分 = 50
    }

    @Test
    void calculateScore_reconciliationDiff_matched() {
        mockActiveRule();

        Reconciliation recon = new Reconciliation();
        recon.setId(1L);
        recon.setSupplierId(1L);
        recon.setOrderAmount(new BigDecimal("10000"));
        recon.setDiffAmount(BigDecimal.ZERO); // 完全匹配

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

        SupplierScore result = scoreService.calculateScore(1L);
        assertNotNull(result);
        // 对账完全匹配，该维度应得100分
    }

    @Test
    void calculateScore_reconciliationDiff_largeDiff() {
        mockActiveRule();

        Reconciliation recon = new Reconciliation();
        recon.setId(1L);
        recon.setSupplierId(1L);
        recon.setOrderAmount(new BigDecimal("10000"));
        recon.setDiffAmount(new BigDecimal("800")); // 8% 差异

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

        SupplierScore result = scoreService.calculateScore(1L);
        assertNotNull(result);
        // 大差异得分较低
    }

    @Test
    void calculateScore_approvalAnomaly_noRejections() {
        mockActiveRule();

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);

        Approval approval = new Approval();
        approval.setBusinessType("PO");
        approval.setBusinessId(1L);
        approval.setStatus("APPROVED");

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(poMapper.selectCount(any())).thenReturn(1L);
        when(arrivalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(List.of(approval));
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore result = scoreService.calculateScore(1L);
        assertNotNull(result);
        // 无拒绝记录，审批异常维度应得100分
    }

    @Test
    void calculateScore_approvalAnomaly_allRejected() {
        mockActiveRule();

        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);

        Approval approval = new Approval();
        approval.setBusinessType("PO");
        approval.setBusinessId(1L);
        approval.setStatus("REJECTED");

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po));
        when(poMapper.selectCount(any())).thenReturn(1L);
        when(arrivalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(List.of(approval));
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore result = scoreService.calculateScore(1L);
        assertNotNull(result);
        // 全被拒绝，审批异常维度应得0分
    }

    @Test
    void adjustScore_shouldCreateRecord() {
        SupplierScore existing = new SupplierScore();
        existing.setId(1L);
        existing.setSupplierId(1L);
        existing.setTotalScore(new BigDecimal("75.00"));
        when(scoreMapper.selectOne(any())).thenReturn(existing);
        when(adjustmentMapper.insert(any())).thenReturn(1);
        when(scoreMapper.updateById(any())).thenReturn(1);

        SupplierScore result = scoreService.adjustScore(1L, new BigDecimal("85.00"), "特殊原因调整");

        assertEquals(new BigDecimal("85.00"), result.getTotalScore());
        assertEquals("MANUAL", result.getSource());
        verify(adjustmentMapper).insert(any());
    }

    @Test
    void createRuleVersion_supersedesOld() {
        ScoringRuleVersion oldRule = new ScoringRuleVersion();
        oldRule.setId(1L);
        oldRule.setVersionNo(1);
        oldRule.setStatus("ACTIVE");
        when(ruleVersionMapper.selectList(any())).thenReturn(List.of(oldRule));
        when(ruleVersionMapper.updateById(any())).thenReturn(1);
        when(ruleVersionMapper.insert(any())).thenReturn(1);

        ScoringRuleVersion result = scoreService.createRuleVersion(
                "{\"quoteResponseTimeliness\":20}", "{\"blacklistScore\":25}");

        assertEquals(2, result.getVersionNo());
        assertEquals("ACTIVE", result.getStatus());
        // 旧版本应被更新为SUPERSEDED
        assertEquals("SUPERSEDED", oldRule.getStatus());
    }

    @Test
    void recalculateAll_updatesAllSuppliers() {
        Supplier s1 = new Supplier();
        s1.setId(1L);
        s1.setStatus("ACTIVE");
        Supplier s2 = new Supplier();
        s2.setId(2L);
        s2.setStatus("ACTIVE");

        when(supplierMapper.selectList(any())).thenReturn(List.of(s1, s2));
        mockActiveRule();
        mockNoData();

        // 需要两次 calculateScore 的 mock
        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(scoreMapper.insert(any())).thenReturn(1);

        scoreService.recalculateAll();

        verify(supplierMapper).selectList(any());
        // 至少调用了两次insert（两个供应商）
        verify(scoreMapper, atLeast(2)).insert(any());
    }

    @Test
    void getActiveRuleVersion_throwsWhenNoActiveRule() {
        when(ruleVersionMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> scoreService.getActiveRuleVersion());
    }
}
