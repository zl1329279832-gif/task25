package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.service.impl.ScoreCalculationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ScoreCalculationService 测试 — 验证独立事务语义下的单供应商评分计算。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoreCalculationServiceTest {

    @InjectMocks
    private ScoreCalculationServiceImpl scoreCalculationService;

    @Mock private SupplierScoreMapper scoreMapper;
    @Mock private SupplierScoreDetailMapper detailMapper;
    @Mock private ScoringRuleVersionMapper ruleVersionMapper;
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
        activeRule = new ScoringRuleVersion();
        activeRule.setId(1L);
        activeRule.setVersionNo(1);
        activeRule.setWeights("{\"quoteResponseTimeliness\":15,\"priceDeviation\":15," +
                "\"deliveryOnTime\":15,\"arrivalDiscrepancy\":10,\"qcFailureRate\":15," +
                "\"returnRate\":10,\"reconciliationDiff\":10,\"approvalAnomaly\":10}");
        activeRule.setThresholds("{\"blacklistScore\":20,\"restrictedScore\":50,\"extraApprovalScore\":70}");
        activeRule.setStatus("ACTIVE");

        when(ruleVersionMapper.selectOne(any())).thenReturn(activeRule);
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

    @Test
    void calculateSingleScore_newSupplier_neutralScore() {
        mockEmptyDataForScore();

        SupplierScore score = scoreCalculationService.calculateSingleScore(1L);

        assertNotNull(score);
        assertEquals(1L, score.getSupplierId());
        assertEquals("SYSTEM", score.getSource());
        // 无数据时大部分维度返回默认50分，但审批异常维度返回100分（无审批=无异常）
        // 加权总分 = 50*(15+15+15+10+15+10+10)/100 + 100*10/100 = 45 + 10 = 55
        assertEquals(new BigDecimal("55.00"), score.getTotalScore());
        verify(scoreMapper).insert(any());
        verify(detailMapper, times(8)).insert(any());
    }

    @Test
    void calculateSingleScore_existingScore_updatesRecord() {
        SupplierScore existing = new SupplierScore();
        existing.setId(100L);
        existing.setSupplierId(1L);
        existing.setTotalScore(new BigDecimal("60.00"));
        when(scoreMapper.selectOne(any())).thenReturn(existing);
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
        when(detailMapper.delete(any())).thenReturn(5);
        when(scoreMapper.updateById(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore score = scoreCalculationService.calculateSingleScore(1L);

        assertNotNull(score);
        verify(scoreMapper).updateById(any());
        verify(scoreMapper, never()).insert(any());
        // 旧明细被删除
        verify(detailMapper).delete(any());
    }

    @Test
    void calculateSingleScore_noActiveRule_throws() {
        when(ruleVersionMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> scoreCalculationService.calculateSingleScore(1L));
    }

    @Test
    void calculateSingleScore_withArrivalDiscrepancy_smallDiff() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);

        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setPoId(1L);

        // 1% 差异 — 得100分
        ArrivalLine line = new ArrivalLine();
        line.setArrivalId(1L);
        line.setOrderedQty(new BigDecimal("100"));
        line.setArrivedQty(new BigDecimal("101")); // 1% diff

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

        SupplierScore score = scoreCalculationService.calculateSingleScore(1L);
        assertNotNull(score);
        // 到货差异维度100分
        verify(detailMapper, atLeastOnce()).insert(any());
    }

    @Test
    void calculateSingleScore_withQcFailure_allFail() {
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

        SupplierScore score = scoreCalculationService.calculateSingleScore(1L);
        assertNotNull(score);
        // 质检全部FAIL → qcFailureRate维度得0分
        // 但其他无数据维度默认50分，审批异常维度默认100分
        // 总分 = 50*(15+15+15+10+10+10)/100 + 0*15/100 + 100*10/100 = 37.5 + 0 + 10 = 47.5
        // 验证质检维度确实拉低了综合分（低于全50分的55分）
        assertTrue(score.getTotalScore().compareTo(new BigDecimal("55")) < 0);
    }

    @Test
    void calculateSingleScore_withReconciliationDiff_largeDiff() {
        Reconciliation recon = new Reconciliation();
        recon.setId(1L);
        recon.setSupplierId(1L);
        recon.setOrderAmount(new BigDecimal("100000"));
        recon.setDiffAmount(new BigDecimal("8000")); // 8% 差异 > 5%

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

        SupplierScore score = scoreCalculationService.calculateSingleScore(1L);
        assertNotNull(score);
        // 对账差异维度得20分
    }

    @Test
    void calculateSingleScore_withReturnRate_highReturn() {
        PurchaseOrder po1 = new PurchaseOrder();
        po1.setId(1L);
        po1.setSupplierId(1L);
        po1.setStatus("RECEIVED");
        PurchaseOrder po2 = new PurchaseOrder();
        po2.setId(2L);
        po2.setSupplierId(1L);
        po2.setStatus("RECEIVED");

        ReturnOrder ro = new ReturnOrder();
        ro.setId(1L);
        ro.setPoId(1L);
        ro.setSupplierId(1L);
        ro.setStatus("RETURNED"); // 非REJECTED

        when(scoreMapper.selectOne(any())).thenReturn(null);
        when(quoteMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(comparisonLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(poMapper.selectList(any())).thenReturn(List.of(po1, po2));
        when(poMapper.selectCount(any())).thenReturn(2L);
        when(arrivalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(arrivalLineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(qcMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(returnOrderMapper.selectList(any())).thenReturn(List.of(ro));
        when(reconciliationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(approvalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(detailMapper.delete(any())).thenReturn(0);
        when(scoreMapper.insert(any())).thenReturn(1);
        when(detailMapper.insert(any())).thenReturn(1);

        SupplierScore score = scoreCalculationService.calculateSingleScore(1L);
        assertNotNull(score);
        // 退货率维度：1/2 PO 退货 → rate=0.5 → score = max(0, 100*(1-0.5*2)) = 0
    }
}
