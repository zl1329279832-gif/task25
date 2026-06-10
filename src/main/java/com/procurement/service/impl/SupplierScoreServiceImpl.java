package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.SupplierScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierScoreServiceImpl implements SupplierScoreService {

    private final ScoreRuleMapper scoreRuleMapper;
    private final SupplierScoreMapper supplierScoreMapper;
    private final SupplierScoreDetailMapper scoreDetailMapper;
    private final SupplierScoreAdjustmentMapper adjustmentMapper;
    private final SupplierScoreSnapshotMapper snapshotMapper;
    private final SupplierMapper supplierMapper;
    private final RfqMapper rfqMapper;
    private final RfqSupplierMapper rfqSupplierMapper;
    private final QuoteMapper quoteMapper;
    private final QuoteLineMapper quoteLineMapper;
    private final ComparisonLineMapper comparisonLineMapper;
    private final PurchaseOrderMapper poMapper;
    private final ArrivalMapper arrivalMapper;
    private final ArrivalLineMapper arrivalLineMapper;
    private final QualityInspectionMapper inspectionMapper;
    private final ReturnOrderMapper returnOrderMapper;
    private final ReturnLineMapper returnLineMapper;
    private final ReconciliationMapper reconciliationMapper;
    private final ApprovalMapper approvalMapper;
    private final ObjectMapper objectMapper;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_SCORE = new BigDecimal("100.00");

    // ==================== 评分计算 ====================

    @Override
    @Transactional
    @Auditable(action = "RECALCULATE_ALL_SCORES", entityType = "SupplierScore")
    public void recalculateAll() {
        List<Supplier> suppliers = supplierMapper.selectList(
                new LambdaQueryWrapper<Supplier>().ne(Supplier::getStatus, "BLACKLISTED"));
        for (Supplier supplier : suppliers) {
            try {
                recalculateSupplier(supplier.getId());
            } catch (Exception e) {
                log.error("供应商 {} 评分计算失败: {}", supplier.getId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    @Auditable(action = "RECALCULATE_SCORE", entityType = "SupplierScore")
    public void recalculateSupplier(Long supplierId) {
        ScoreRule rule = getActiveRule();
        if (rule == null) {
            throw new BusinessException("未找到生效的评分规则，请先创建并激活规则");
        }

        LocalDateTime now = LocalDateTime.now();

        // 计算8个维度得分
        BigDecimal quoteResponseScore = calcQuoteResponseScore(supplierId);
        BigDecimal priceDeviationScore = calcPriceDeviationScore(supplierId);
        BigDecimal deliveryOnTimeScore = calcDeliveryOnTimeScore(supplierId);
        BigDecimal arrivalDiffScore = calcArrivalDiffScore(supplierId);
        BigDecimal qcRejectScore = calcQcRejectScore(supplierId);
        BigDecimal returnRateScore = calcReturnRateScore(supplierId);
        BigDecimal invoiceDiffScore = calcInvoiceDiffScore(supplierId);
        BigDecimal approvalAnomalyScore = calcApprovalAnomalyScore(supplierId);

        // 加权计算总分
        BigDecimal totalScore = quoteResponseScore.multiply(rule.getQuoteResponseWeight())
                .add(priceDeviationScore.multiply(rule.getPriceDeviationWeight()))
                .add(deliveryOnTimeScore.multiply(rule.getDeliveryOnTimeWeight()))
                .add(arrivalDiffScore.multiply(rule.getArrivalDiffWeight()))
                .add(qcRejectScore.multiply(rule.getQcRejectWeight()))
                .add(returnRateScore.multiply(rule.getReturnRateWeight()))
                .add(invoiceDiffScore.multiply(rule.getInvoiceDiffWeight()))
                .add(approvalAnomalyScore.multiply(rule.getApprovalAnomalyWeight()))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        String level = ScoreLevel.fromScore(totalScore).name();

        // 保存评分明细
        saveDetail(supplierId, rule.getVersion(), "QUOTE_RESPONSE", quoteResponseScore, rule.getQuoteResponseWeight(), now);
        saveDetail(supplierId, rule.getVersion(), "PRICE_DEVIATION", priceDeviationScore, rule.getPriceDeviationWeight(), now);
        saveDetail(supplierId, rule.getVersion(), "DELIVERY_ON_TIME", deliveryOnTimeScore, rule.getDeliveryOnTimeWeight(), now);
        saveDetail(supplierId, rule.getVersion(), "ARRIVAL_DIFF", arrivalDiffScore, rule.getArrivalDiffWeight(), now);
        saveDetail(supplierId, rule.getVersion(), "QC_REJECT", qcRejectScore, rule.getQcRejectWeight(), now);
        saveDetail(supplierId, rule.getVersion(), "RETURN_RATE", returnRateScore, rule.getReturnRateWeight(), now);
        saveDetail(supplierId, rule.getVersion(), "INVOICE_DIFF", invoiceDiffScore, rule.getInvoiceDiffWeight(), now);
        saveDetail(supplierId, rule.getVersion(), "APPROVAL_ANOMALY", approvalAnomalyScore, rule.getApprovalAnomalyWeight(), now);

        // 更新或插入供应商评分汇总
        SupplierScore score = supplierScoreMapper.selectOne(
                new LambdaQueryWrapper<SupplierScore>().eq(SupplierScore::getSupplierId, supplierId));

        if (score == null) {
            score = new SupplierScore();
            score.setSupplierId(supplierId);
            score.setRuleVersion(rule.getVersion());
            score.setTotalScore(totalScore);
            score.setQuoteResponseScore(quoteResponseScore);
            score.setPriceDeviationScore(priceDeviationScore);
            score.setDeliveryOnTimeScore(deliveryOnTimeScore);
            score.setArrivalDiffScore(arrivalDiffScore);
            score.setQcRejectScore(qcRejectScore);
            score.setReturnRateScore(returnRateScore);
            score.setInvoiceDiffScore(invoiceDiffScore);
            score.setApprovalAnomalyScore(approvalAnomalyScore);
            score.setScoreLevel(level);
            score.setCalculatedAt(now);
            supplierScoreMapper.insert(score);
        } else {
            score.setRuleVersion(rule.getVersion());
            score.setTotalScore(totalScore);
            score.setQuoteResponseScore(quoteResponseScore);
            score.setPriceDeviationScore(priceDeviationScore);
            score.setDeliveryOnTimeScore(deliveryOnTimeScore);
            score.setArrivalDiffScore(arrivalDiffScore);
            score.setQcRejectScore(qcRejectScore);
            score.setReturnRateScore(returnRateScore);
            score.setInvoiceDiffScore(invoiceDiffScore);
            score.setApprovalAnomalyScore(approvalAnomalyScore);
            score.setScoreLevel(level);
            score.setCalculatedAt(now);
            supplierScoreMapper.updateById(score);
        }
    }

    // ==================== 8个维度计算 ====================

    /**
     * 报价响应时效：在截止时间前提交的比率
     */
    BigDecimal calcQuoteResponseScore(Long supplierId) {
        List<RfqSupplier> invitations = rfqSupplierMapper.selectList(
                new LambdaQueryWrapper<RfqSupplier>().eq(RfqSupplier::getSupplierId, supplierId));
        if (invitations.isEmpty()) return DEFAULT_SCORE;

        int totalInvited = 0;
        int timelyResponses = 0;

        for (RfqSupplier inv : invitations) {
            Rfq rfq = rfqMapper.selectById(inv.getRfqId());
            if (rfq == null) continue;

            totalInvited++;
            // 获取该供应商对此RFQ的最新报价
            Quote quote = quoteMapper.selectOne(
                    new LambdaQueryWrapper<Quote>()
                            .eq(Quote::getRfqId, inv.getRfqId())
                            .eq(Quote::getSupplierId, supplierId)
                            .orderByDesc(Quote::getVersion)
                            .last("LIMIT 1"));

            if (quote != null && quote.getSubmittedAt() != null
                    && !quote.getSubmittedAt().isAfter(rfq.getDeadline())) {
                timelyResponses++;
            }
        }

        if (totalInvited == 0) return DEFAULT_SCORE;
        return new BigDecimal(timelyResponses * 100)
                .divide(new BigDecimal(totalInvited), 2, RoundingMode.HALF_UP);
    }

    /**
     * 价格偏离：报价金额与最低价的偏差
     */
    BigDecimal calcPriceDeviationScore(Long supplierId) {
        List<ComparisonLine> lines = comparisonLineMapper.selectList(
                new LambdaQueryWrapper<ComparisonLine>().eq(ComparisonLine::getSupplierId, supplierId));
        if (lines.isEmpty()) return DEFAULT_SCORE;

        // 按comparison_id分组
        Map<Long, List<ComparisonLine>> byComparison = lines.stream()
                .collect(Collectors.groupingBy(ComparisonLine::getComparisonId));

        BigDecimal totalDeviation = BigDecimal.ZERO;
        int count = 0;

        for (Map.Entry<Long, List<ComparisonLine>> entry : byComparison.entrySet()) {
            // 获取该比价的所有行
            List<ComparisonLine> allLines = comparisonLineMapper.selectList(
                    new LambdaQueryWrapper<ComparisonLine>()
                            .eq(ComparisonLine::getComparisonId, entry.getKey()));

            BigDecimal minAmount = allLines.stream()
                    .map(ComparisonLine::getTotalAmount)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(BigDecimal.ONE);

            for (ComparisonLine supplierLine : entry.getValue()) {
                if (supplierLine.getTotalAmount() != null && minAmount.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal deviation = supplierLine.getTotalAmount().subtract(minAmount)
                            .divide(minAmount, 4, RoundingMode.HALF_UP);
                    totalDeviation = totalDeviation.add(deviation);
                    count++;
                }
            }
        }

        if (count == 0) return DEFAULT_SCORE;
        BigDecimal avgDeviation = totalDeviation.divide(new BigDecimal(count), 4, RoundingMode.HALF_UP);
        // 偏差0%=100分，每偏离1%扣1分
        BigDecimal score = HUNDRED.subtract(avgDeviation.multiply(HUNDRED));
        return score.max(BigDecimal.ZERO).min(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 交付准时率：实际到货天数 vs 承诺交期
     */
    BigDecimal calcDeliveryOnTimeScore(Long supplierId) {
        List<PurchaseOrder> pos = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getSupplierId, supplierId));
        if (pos.isEmpty()) return DEFAULT_SCORE;

        int totalDeliveries = 0;
        int onTimeDeliveries = 0;

        for (PurchaseOrder po : pos) {
            List<Arrival> arrivals = arrivalMapper.selectList(
                    new LambdaQueryWrapper<Arrival>().eq(Arrival::getPoId, po.getId()));
            if (arrivals.isEmpty()) continue;

            // 获取承诺交期（从比价关联的报价行获取）
            Integer promisedDays = getPromisedDeliveryDays(po);
            if (promisedDays == null || promisedDays <= 0) continue;

            for (Arrival arrival : arrivals) {
                totalDeliveries++;
                long actualDays = ChronoUnit.DAYS.between(po.getCreatedAt(), arrival.getArrivedAt());
                if (actualDays <= promisedDays) {
                    onTimeDeliveries++;
                }
            }
        }

        if (totalDeliveries == 0) return DEFAULT_SCORE;
        return new BigDecimal(onTimeDeliveries * 100)
                .divide(new BigDecimal(totalDeliveries), 2, RoundingMode.HALF_UP);
    }

    /**
     * 到货差异：到货数量与订单数量的吻合度
     */
    BigDecimal calcArrivalDiffScore(Long supplierId) {
        List<PurchaseOrder> pos = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getSupplierId, supplierId));
        if (pos.isEmpty()) return DEFAULT_SCORE;

        BigDecimal totalDiffRatio = BigDecimal.ZERO;
        int count = 0;

        for (PurchaseOrder po : pos) {
            List<Arrival> arrivals = arrivalMapper.selectList(
                    new LambdaQueryWrapper<Arrival>().eq(Arrival::getPoId, po.getId()));

            for (Arrival arrival : arrivals) {
                List<ArrivalLine> lines = arrivalLineMapper.selectList(
                        new LambdaQueryWrapper<ArrivalLine>().eq(ArrivalLine::getArrivalId, arrival.getId()));

                for (ArrivalLine line : lines) {
                    if (line.getOrderedQty() != null && line.getOrderedQty().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal diff = line.getArrivedQty().subtract(line.getOrderedQty()).abs();
                        BigDecimal ratio = diff.divide(line.getOrderedQty(), 4, RoundingMode.HALF_UP);
                        totalDiffRatio = totalDiffRatio.add(ratio);
                        count++;
                    }
                }
            }
        }

        if (count == 0) return DEFAULT_SCORE;
        BigDecimal avgRatio = totalDiffRatio.divide(new BigDecimal(count), 4, RoundingMode.HALF_UP);
        BigDecimal score = HUNDRED.subtract(avgRatio.multiply(HUNDRED));
        return score.max(BigDecimal.ZERO).min(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 质检不合格率
     */
    BigDecimal calcQcRejectScore(Long supplierId) {
        List<PurchaseOrder> pos = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getSupplierId, supplierId));
        if (pos.isEmpty()) return DEFAULT_SCORE;

        List<Long> poIds = pos.stream().map(PurchaseOrder::getId).collect(Collectors.toList());
        List<Arrival> arrivals = arrivalMapper.selectList(
                new LambdaQueryWrapper<Arrival>().in(Arrival::getPoId, poIds));
        if (arrivals.isEmpty()) return DEFAULT_SCORE;

        List<Long> arrivalIds = arrivals.stream().map(Arrival::getId).collect(Collectors.toList());
        List<QualityInspection> inspections = inspectionMapper.selectList(
                new LambdaQueryWrapper<QualityInspection>().in(QualityInspection::getArrivalId, arrivalIds));
        if (inspections.isEmpty()) return DEFAULT_SCORE;

        long failCount = inspections.stream()
                .filter(qi -> "FAIL".equals(qi.getResult()) || "CONDITIONAL".equals(qi.getResult()))
                .count();

        BigDecimal rejectRate = new BigDecimal(failCount)
                .divide(new BigDecimal(inspections.size()), 4, RoundingMode.HALF_UP);
        BigDecimal score = HUNDRED.subtract(rejectRate.multiply(HUNDRED));
        return score.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 退货率
     */
    BigDecimal calcReturnRateScore(Long supplierId) {
        List<ReturnOrder> returnOrders = returnOrderMapper.selectList(
                new LambdaQueryWrapper<ReturnOrder>().eq(ReturnOrder::getSupplierId, supplierId));

        // 获取总到货数量
        List<PurchaseOrder> pos = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getSupplierId, supplierId));
        if (pos.isEmpty()) return DEFAULT_SCORE;

        List<Long> poIds = pos.stream().map(PurchaseOrder::getId).collect(Collectors.toList());
        List<Arrival> arrivals = arrivalMapper.selectList(
                new LambdaQueryWrapper<Arrival>().in(Arrival::getPoId, poIds));
        if (arrivals.isEmpty()) return DEFAULT_SCORE;

        List<Long> arrivalIds = arrivals.stream().map(Arrival::getId).collect(Collectors.toList());
        List<ArrivalLine> arrivalLines = arrivalLineMapper.selectList(
                new LambdaQueryWrapper<ArrivalLine>().in(ArrivalLine::getArrivalId, arrivalIds));

        BigDecimal totalArrived = arrivalLines.stream()
                .map(ArrivalLine::getArrivedQty)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalArrived.compareTo(BigDecimal.ZERO) == 0) return DEFAULT_SCORE;

        // 获取退货总量
        BigDecimal totalReturned = BigDecimal.ZERO;
        if (!returnOrders.isEmpty()) {
            List<Long> returnIds = returnOrders.stream().map(ReturnOrder::getId).collect(Collectors.toList());
            List<ReturnLine> returnLines = returnLineMapper.selectList(
                    new LambdaQueryWrapper<ReturnLine>().in(ReturnLine::getReturnId, returnIds));
            totalReturned = returnLines.stream()
                    .map(ReturnLine::getQuantity)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal returnRate = totalReturned.divide(totalArrived, 4, RoundingMode.HALF_UP);
        BigDecimal score = HUNDRED.subtract(returnRate.multiply(HUNDRED));
        return score.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 发票对账差异
     */
    BigDecimal calcInvoiceDiffScore(Long supplierId) {
        List<Reconciliation> recons = reconciliationMapper.selectList(
                new LambdaQueryWrapper<Reconciliation>().eq(Reconciliation::getSupplierId, supplierId));
        if (recons.isEmpty()) return DEFAULT_SCORE;

        BigDecimal totalDiffRatio = BigDecimal.ZERO;
        int count = 0;

        for (Reconciliation recon : recons) {
            if (recon.getOrderAmount() != null && recon.getOrderAmount().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diffAmount = recon.getInvoiceAmount().subtract(recon.getReceiptAmount()).abs();
                BigDecimal ratio = diffAmount.divide(recon.getOrderAmount(), 4, RoundingMode.HALF_UP);
                totalDiffRatio = totalDiffRatio.add(ratio);
                count++;
            }
        }

        if (count == 0) return DEFAULT_SCORE;
        BigDecimal avgRatio = totalDiffRatio.divide(new BigDecimal(count), 4, RoundingMode.HALF_UP);
        BigDecimal score = HUNDRED.subtract(avgRatio.multiply(HUNDRED));
        return score.max(BigDecimal.ZERO).min(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 审批异常记录：关联此供应商的PO审批被驳回率
     */
    BigDecimal calcApprovalAnomalyScore(Long supplierId) {
        List<PurchaseOrder> pos = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getSupplierId, supplierId));
        if (pos.isEmpty()) return DEFAULT_SCORE;

        List<Long> poIds = pos.stream().map(PurchaseOrder::getId).collect(Collectors.toList());
        List<Approval> approvals = approvalMapper.selectList(
                new LambdaQueryWrapper<Approval>()
                        .eq(Approval::getBusinessType, "PO")
                        .in(Approval::getBusinessId, poIds));
        if (approvals.isEmpty()) return DEFAULT_SCORE;

        long rejectedCount = approvals.stream()
                .filter(a -> "REJECTED".equals(a.getStatus()))
                .count();

        BigDecimal rejectRate = new BigDecimal(rejectedCount)
                .divide(new BigDecimal(approvals.size()), 4, RoundingMode.HALF_UP);
        BigDecimal score = HUNDRED.subtract(rejectRate.multiply(HUNDRED));
        return score.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    // ==================== 规则管理 ====================

    @Override
    @Transactional
    @Auditable(action = "CREATE_SCORE_RULE", entityType = "ScoreRule")
    public ScoreRule createRule(ScoreRule rule) {
        // 验证权重总和为100
        BigDecimal weightSum = rule.getQuoteResponseWeight()
                .add(rule.getPriceDeviationWeight())
                .add(rule.getDeliveryOnTimeWeight())
                .add(rule.getArrivalDiffWeight())
                .add(rule.getQcRejectWeight())
                .add(rule.getReturnRateWeight())
                .add(rule.getInvoiceDiffWeight())
                .add(rule.getApprovalAnomalyWeight());
        if (weightSum.compareTo(HUNDRED) != 0) {
            throw new BusinessException("权重总和必须为100，当前为: " + weightSum);
        }

        LoginUser user = getCurrentUser();
        rule.setCreatedBy(user.getUserId());
        rule.setActive(0);
        scoreRuleMapper.insert(rule);
        return rule;
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE_SCORE_RULE", entityType = "ScoreRule")
    public ScoreRule activateRule(Long ruleId) {
        ScoreRule rule = scoreRuleMapper.selectById(ruleId);
        if (rule == null) throw new BusinessException("评分规则不存在");

        // 停用所有旧规则
        List<ScoreRule> allRules = scoreRuleMapper.selectList(
                new LambdaQueryWrapper<ScoreRule>().eq(ScoreRule::getActive, 1));
        for (ScoreRule r : allRules) {
            r.setActive(0);
            scoreRuleMapper.updateById(r);
        }

        // 激活新规则
        rule.setActive(1);
        scoreRuleMapper.updateById(rule);
        return rule;
    }

    @Override
    public ScoreRule getActiveRule() {
        return scoreRuleMapper.selectOne(
                new LambdaQueryWrapper<ScoreRule>().eq(ScoreRule::getActive, 1));
    }

    // ==================== 人工调整 ====================

    @Override
    @Transactional
    @Auditable(action = "ADJUST_SCORE", entityType = "SupplierScore")
    public void adjustScore(Long supplierId, String dimension, BigDecimal newScore, String reason) {
        if (newScore.compareTo(BigDecimal.ZERO) < 0 || newScore.compareTo(HUNDRED) > 0) {
            throw new BusinessException("评分必须在0-100之间");
        }

        SupplierScore score = supplierScoreMapper.selectOne(
                new LambdaQueryWrapper<SupplierScore>().eq(SupplierScore::getSupplierId, supplierId));
        if (score == null) throw new BusinessException("供应商评分不存在，请先执行评分计算");

        BigDecimal originalScore;
        if (!StringUtils.hasText(dimension)) {
            // 调整总分
            originalScore = score.getTotalScore();
            score.setTotalScore(newScore);
        } else {
            originalScore = getDimensionScore(score, dimension);
            setDimensionScore(score, dimension, newScore);
            // 使用当前规则重新计算加权总分
            ScoreRule rule = getActiveRule();
            if (rule != null) {
                score.setTotalScore(calcWeightedTotal(score, rule));
            }
        }

        score.setScoreLevel(ScoreLevel.fromScore(score.getTotalScore()).name());
        supplierScoreMapper.updateById(score);

        // 记录调整
        LoginUser user = getCurrentUser();
        SupplierScoreAdjustment adjustment = new SupplierScoreAdjustment();
        adjustment.setSupplierId(supplierId);
        adjustment.setDimension(dimension);
        adjustment.setOriginalScore(originalScore);
        adjustment.setAdjustedScore(newScore);
        adjustment.setReason(reason);
        adjustment.setAdjustedBy(user.getUserId());
        adjustmentMapper.insert(adjustment);
    }

    // ==================== 查询 ====================

    @Override
    public SupplierScore getScore(Long supplierId) {
        SupplierScore score = supplierScoreMapper.selectOne(
                new LambdaQueryWrapper<SupplierScore>().eq(SupplierScore::getSupplierId, supplierId));
        if (score == null) {
            // 无评分记录的供应商返回默认满分
            score = buildDefaultScore(supplierId);
        }
        return score;
    }

    @Override
    public Page<SupplierScore> listScores(String level, int page, int size) {
        LambdaQueryWrapper<SupplierScore> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(level)) {
            wrapper.eq(SupplierScore::getScoreLevel, level);
        }
        wrapper.orderByAsc(SupplierScore::getTotalScore);
        return supplierScoreMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<SupplierScoreDetail> getScoreDetails(Long supplierId) {
        return scoreDetailMapper.selectList(
                new LambdaQueryWrapper<SupplierScoreDetail>()
                        .eq(SupplierScoreDetail::getSupplierId, supplierId)
                        .orderByDesc(SupplierScoreDetail::getCalculatedAt)
                        .last("LIMIT 8"));
    }

    @Override
    public SupplierScoreSnapshot getSnapshot(String businessType, Long businessId) {
        return snapshotMapper.selectOne(
                new LambdaQueryWrapper<SupplierScoreSnapshot>()
                        .eq(SupplierScoreSnapshot::getBusinessType, businessType)
                        .eq(SupplierScoreSnapshot::getBusinessId, businessId));
    }

    // ==================== 准入校验 ====================

    @Override
    public void checkSupplierAccess(Long supplierId, String operation) {
        // 先检查供应商状态
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) throw new BusinessException("供应商不存在");
        if ("BLACKLISTED".equals(supplier.getStatus())) {
            throw new BusinessException("供应商 " + supplier.getName() + " 已被拉黑，禁止 " + operation);
        }

        SupplierScore score = getScore(supplierId);
        ScoreLevel level = ScoreLevel.fromScore(score.getTotalScore());

        if (level == ScoreLevel.BLOCKED) {
            throw new BusinessException("供应商 " + supplier.getName()
                    + " 履约评分过低(" + score.getTotalScore() + "分)，禁止 " + operation);
        }

        if (level == ScoreLevel.WARNING) {
            throw new BusinessException("供应商 " + supplier.getName()
                    + " 履约评分预警(" + score.getTotalScore() + "分)，" + operation + " 需要额外审批");
        }
    }

    @Override
    @Transactional
    public SupplierScoreSnapshot createSnapshot(Long supplierId, String businessType, Long businessId) {
        SupplierScore score = getScore(supplierId);

        SupplierScoreSnapshot snapshot = new SupplierScoreSnapshot();
        snapshot.setSupplierId(supplierId);
        snapshot.setBusinessType(businessType);
        snapshot.setBusinessId(businessId);
        snapshot.setRuleVersion(score.getRuleVersion() != null ? score.getRuleVersion() : 0);
        snapshot.setTotalScore(score.getTotalScore());
        snapshot.setScoreLevel(score.getScoreLevel());
        snapshot.setSnapshotAt(LocalDateTime.now());

        // 将各维度评分序列化为JSON
        try {
            Map<String, BigDecimal> detail = new LinkedHashMap<>();
            detail.put("QUOTE_RESPONSE", score.getQuoteResponseScore());
            detail.put("PRICE_DEVIATION", score.getPriceDeviationScore());
            detail.put("DELIVERY_ON_TIME", score.getDeliveryOnTimeScore());
            detail.put("ARRIVAL_DIFF", score.getArrivalDiffScore());
            detail.put("QC_REJECT", score.getQcRejectScore());
            detail.put("RETURN_RATE", score.getReturnRateScore());
            detail.put("INVOICE_DIFF", score.getInvoiceDiffScore());
            detail.put("APPROVAL_ANOMALY", score.getApprovalAnomalyScore());
            snapshot.setScoreDetail(objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            log.warn("评分快照序列化失败: {}", e.getMessage());
        }

        snapshotMapper.insert(snapshot);
        return snapshot;
    }

    // ==================== 辅助方法 ====================

    private void saveDetail(Long supplierId, int ruleVersion, String dimension,
                            BigDecimal score, BigDecimal weight, LocalDateTime calculatedAt) {
        SupplierScoreDetail detail = new SupplierScoreDetail();
        detail.setSupplierId(supplierId);
        detail.setRuleVersion(ruleVersion);
        detail.setDimension(dimension);
        detail.setRawValue(score);
        detail.setScore(score);
        detail.setWeightedScore(score.multiply(weight).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        detail.setCalculatedAt(calculatedAt);
        scoreDetailMapper.insert(detail);
    }

    private Integer getPromisedDeliveryDays(PurchaseOrder po) {
        if (po.getComparisonId() == null) return null;
        ComparisonLine cl = comparisonLineMapper.selectOne(
                new LambdaQueryWrapper<ComparisonLine>()
                        .eq(ComparisonLine::getComparisonId, po.getComparisonId())
                        .eq(ComparisonLine::getSupplierId, po.getSupplierId())
                        .eq(ComparisonLine::getSelected, 1));
        if (cl == null || cl.getAvgDelivery() == null) return null;
        return cl.getAvgDelivery();
    }

    private BigDecimal getDimensionScore(SupplierScore score, String dimension) {
        return switch (dimension) {
            case "QUOTE_RESPONSE" -> score.getQuoteResponseScore();
            case "PRICE_DEVIATION" -> score.getPriceDeviationScore();
            case "DELIVERY_ON_TIME" -> score.getDeliveryOnTimeScore();
            case "ARRIVAL_DIFF" -> score.getArrivalDiffScore();
            case "QC_REJECT" -> score.getQcRejectScore();
            case "RETURN_RATE" -> score.getReturnRateScore();
            case "INVOICE_DIFF" -> score.getInvoiceDiffScore();
            case "APPROVAL_ANOMALY" -> score.getApprovalAnomalyScore();
            default -> throw new BusinessException("未知的评分维度: " + dimension);
        };
    }

    private void setDimensionScore(SupplierScore score, String dimension, BigDecimal value) {
        switch (dimension) {
            case "QUOTE_RESPONSE" -> score.setQuoteResponseScore(value);
            case "PRICE_DEVIATION" -> score.setPriceDeviationScore(value);
            case "DELIVERY_ON_TIME" -> score.setDeliveryOnTimeScore(value);
            case "ARRIVAL_DIFF" -> score.setArrivalDiffScore(value);
            case "QC_REJECT" -> score.setQcRejectScore(value);
            case "RETURN_RATE" -> score.setReturnRateScore(value);
            case "INVOICE_DIFF" -> score.setInvoiceDiffScore(value);
            case "APPROVAL_ANOMALY" -> score.setApprovalAnomalyScore(value);
            default -> throw new BusinessException("未知的评分维度: " + dimension);
        }
    }

    private BigDecimal calcWeightedTotal(SupplierScore score, ScoreRule rule) {
        return score.getQuoteResponseScore().multiply(rule.getQuoteResponseWeight())
                .add(score.getPriceDeviationScore().multiply(rule.getPriceDeviationWeight()))
                .add(score.getDeliveryOnTimeScore().multiply(rule.getDeliveryOnTimeWeight()))
                .add(score.getArrivalDiffScore().multiply(rule.getArrivalDiffWeight()))
                .add(score.getQcRejectScore().multiply(rule.getQcRejectWeight()))
                .add(score.getReturnRateScore().multiply(rule.getReturnRateWeight()))
                .add(score.getInvoiceDiffScore().multiply(rule.getInvoiceDiffWeight()))
                .add(score.getApprovalAnomalyScore().multiply(rule.getApprovalAnomalyWeight()))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private SupplierScore buildDefaultScore(Long supplierId) {
        SupplierScore score = new SupplierScore();
        score.setSupplierId(supplierId);
        score.setRuleVersion(0);
        score.setTotalScore(DEFAULT_SCORE);
        score.setQuoteResponseScore(DEFAULT_SCORE);
        score.setPriceDeviationScore(DEFAULT_SCORE);
        score.setDeliveryOnTimeScore(DEFAULT_SCORE);
        score.setArrivalDiffScore(DEFAULT_SCORE);
        score.setQcRejectScore(DEFAULT_SCORE);
        score.setReturnRateScore(DEFAULT_SCORE);
        score.setInvoiceDiffScore(DEFAULT_SCORE);
        score.setApprovalAnomalyScore(DEFAULT_SCORE);
        score.setScoreLevel(ScoreLevel.EXCELLENT.name());
        score.setCalculatedAt(LocalDateTime.now());
        return score;
    }

    private LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            return lu;
        }
        throw new BusinessException("未登录");
    }
}
