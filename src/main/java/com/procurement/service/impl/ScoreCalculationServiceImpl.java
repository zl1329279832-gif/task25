package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.service.ScoreCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 单供应商评分计算服务 — 每个供应商在独立新事务中完成评分重算。
 * 避免 recalculateAll 长事务导致的行锁争用和并发准入检查读到部分更新状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreCalculationServiceImpl implements ScoreCalculationService {

    private final SupplierScoreMapper scoreMapper;
    private final SupplierScoreDetailMapper detailMapper;
    private final ScoringRuleVersionMapper ruleVersionMapper;
    private final QuoteMapper quoteMapper;
    private final RfqMapper rfqMapper;
    private final ComparisonLineMapper comparisonLineMapper;
    private final ComparisonMapper comparisonMapper;
    private final PurchaseOrderMapper poMapper;
    private final PurchaseOrderLineMapper poLineMapper;
    private final ArrivalMapper arrivalMapper;
    private final ArrivalLineMapper arrivalLineMapper;
    private final QualityInspectionMapper qcMapper;
    private final ReturnOrderMapper returnOrderMapper;
    private final ReconciliationMapper reconciliationMapper;
    private final ApprovalMapper approvalMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SupplierScore calculateSingleScore(Long supplierId) {
        ScoringRuleVersion rule = getActiveRuleVersion();
        Map<String, BigDecimal> weights = parseWeights(rule.getWeights());

        // 删除旧明细
        SupplierScore existing = scoreMapper.selectOne(
                new LambdaQueryWrapper<SupplierScore>()
                        .eq(SupplierScore::getSupplierId, supplierId));
        if (existing != null) {
            detailMapper.delete(new LambdaQueryWrapper<SupplierScoreDetail>()
                    .eq(SupplierScoreDetail::getSupplierScoreId, existing.getId()));
        }

        // 计算8个维度
        List<SupplierScoreDetail> details = new ArrayList<>();
        BigDecimal totalScore = BigDecimal.ZERO;
        int sampleSize = countSampleSize(supplierId);

        BigDecimal qrtScore = calcQuoteResponseTimeliness(supplierId);
        totalScore = totalScore.add(addDetail(details, "quoteResponseTimeliness",
                null, qrtScore, weights.getOrDefault("quoteResponseTimeliness", BigDecimal.valueOf(15))));

        BigDecimal pdScore = calcPriceDeviation(supplierId);
        totalScore = totalScore.add(addDetail(details, "priceDeviation",
                null, pdScore, weights.getOrDefault("priceDeviation", BigDecimal.valueOf(15))));

        BigDecimal dotScore = calcDeliveryOnTime(supplierId);
        totalScore = totalScore.add(addDetail(details, "deliveryOnTime",
                null, dotScore, weights.getOrDefault("deliveryOnTime", BigDecimal.valueOf(15))));

        BigDecimal adScore = calcArrivalDiscrepancy(supplierId);
        totalScore = totalScore.add(addDetail(details, "arrivalDiscrepancy",
                null, adScore, weights.getOrDefault("arrivalDiscrepancy", BigDecimal.valueOf(10))));

        BigDecimal qcScore = calcQcFailureRate(supplierId);
        totalScore = totalScore.add(addDetail(details, "qcFailureRate",
                null, qcScore, weights.getOrDefault("qcFailureRate", BigDecimal.valueOf(15))));

        BigDecimal rrScore = calcReturnRate(supplierId);
        totalScore = totalScore.add(addDetail(details, "returnRate",
                null, rrScore, weights.getOrDefault("returnRate", BigDecimal.valueOf(10))));

        BigDecimal rdScore = calcReconciliationDiff(supplierId);
        totalScore = totalScore.add(addDetail(details, "reconciliationDiff",
                null, rdScore, weights.getOrDefault("reconciliationDiff", BigDecimal.valueOf(10))));

        BigDecimal aaScore = calcApprovalAnomaly(supplierId);
        totalScore = totalScore.add(addDetail(details, "approvalAnomaly",
                null, aaScore, weights.getOrDefault("approvalAnomaly", BigDecimal.valueOf(10))));

        // 保存或更新评分
        if (existing == null) {
            existing = new SupplierScore();
            existing.setSupplierId(supplierId);
            existing.setRuleVersionId(rule.getId());
            existing.setTotalScore(totalScore);
            existing.setSampleSize(sampleSize);
            existing.setCalculatedAt(LocalDateTime.now());
            existing.setSource("SYSTEM");
            scoreMapper.insert(existing);
        } else {
            existing.setRuleVersionId(rule.getId());
            existing.setTotalScore(totalScore);
            existing.setSampleSize(sampleSize);
            existing.setCalculatedAt(LocalDateTime.now());
            existing.setSource("SYSTEM");
            scoreMapper.updateById(existing);
        }

        // 保存明细
        for (SupplierScoreDetail d : details) {
            d.setSupplierScoreId(existing.getId());
            detailMapper.insert(d);
        }

        return existing;
    }

    // ==================== 8个维度计算方法 ====================

    private BigDecimal calcQuoteResponseTimeliness(Long supplierId) {
        List<Quote> quotes = quoteMapper.selectList(
                new LambdaQueryWrapper<Quote>()
                        .eq(Quote::getSupplierId, supplierId)
                        .isNotNull(Quote::getSubmittedAt));

        if (quotes.isEmpty()) return BigDecimal.valueOf(50);

        double totalScore = 0;
        int count = 0;

        for (Quote q : quotes) {
            Rfq rfq = rfqMapper.selectById(q.getRfqId());
            if (rfq == null) continue;

            long totalWindow = ChronoUnit.MINUTES.between(rfq.getCreatedAt(), rfq.getDeadline());
            if (totalWindow <= 0) continue;

            long responseTime = ChronoUnit.MINUTES.between(rfq.getCreatedAt(), q.getSubmittedAt());
            double ratio = (double) responseTime / totalWindow;

            double score;
            if (ratio >= 0.8) {
                score = 100;
            } else if (ratio >= 0.5) {
                score = 60 + 40 * (ratio - 0.5) / 0.3;
            } else {
                score = 60 * ratio / 0.5;
            }
            totalScore += score;
            count++;
        }

        return count > 0 ? BigDecimal.valueOf(totalScore / count)
                .setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(50);
    }

    private BigDecimal calcPriceDeviation(Long supplierId) {
        List<ComparisonLine> allLines = comparisonLineMapper.selectList(
                new LambdaQueryWrapper<ComparisonLine>()
                        .eq(ComparisonLine::getSupplierId, supplierId));

        if (allLines.isEmpty()) return BigDecimal.valueOf(50);

        double totalScore = 0;
        int count = 0;

        for (ComparisonLine line : allLines) {
            List<ComparisonLine> peerLines = comparisonLineMapper.selectList(
                    new LambdaQueryWrapper<ComparisonLine>()
                            .eq(ComparisonLine::getComparisonId, line.getComparisonId())
                            .orderByAsc(ComparisonLine::getRankNo));

            int totalSuppliers = peerLines.size();
            if (totalSuppliers <= 1) {
                totalScore += 100;
            } else {
                int rank = line.getRankNo() != null ? line.getRankNo() : totalSuppliers;
                double rankRatio = (double) (rank - 1) / (totalSuppliers - 1);
                totalScore += 100 * (1 - rankRatio);
            }
            count++;
        }

        return count > 0 ? BigDecimal.valueOf(totalScore / count)
                .setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(50);
    }

    private BigDecimal calcDeliveryOnTime(Long supplierId) {
        List<PurchaseOrder> pos = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getSupplierId, supplierId)
                        .in(PurchaseOrder::getStatus, "RECEIVED", "PARTIAL_RECEIVED", "CONFIRMED"));

        if (pos.isEmpty()) return BigDecimal.valueOf(50);

        double totalScore = 0;
        int count = 0;

        for (PurchaseOrder po : pos) {
            List<Arrival> arrivals = arrivalMapper.selectList(
                    new LambdaQueryWrapper<Arrival>()
                            .eq(Arrival::getPoId, po.getId())
                            .orderByAsc(Arrival::getArrivedAt));

            if (arrivals.isEmpty()) continue;

            int expectedDays = getExpectedDeliveryDays(po);
            if (expectedDays <= 0) expectedDays = 30;

            LocalDateTime confirmedAt = po.getUpdatedAt() != null ? po.getUpdatedAt() : po.getCreatedAt();
            if (confirmedAt == null) continue;
            long actualDays = ChronoUnit.DAYS.between(confirmedAt, arrivals.get(0).getArrivedAt());

            double score;
            if (actualDays <= expectedDays) {
                score = 100;
            } else if (actualDays <= expectedDays * 1.2) {
                score = 80;
            } else if (actualDays <= expectedDays * 1.5) {
                score = 50;
            } else {
                score = 20;
            }
            totalScore += score;
            count++;
        }

        return count > 0 ? BigDecimal.valueOf(totalScore / count)
                .setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(50);
    }

    private BigDecimal calcArrivalDiscrepancy(Long supplierId) {
        List<PurchaseOrder> pos = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getSupplierId, supplierId));

        List<Long> poIds = pos.stream().map(PurchaseOrder::getId).collect(Collectors.toList());
        if (poIds.isEmpty()) return BigDecimal.valueOf(50);

        List<Arrival> arrivals = arrivalMapper.selectList(
                new LambdaQueryWrapper<Arrival>().in(Arrival::getPoId, poIds));
        List<Long> arrivalIds = arrivals.stream().map(Arrival::getId).collect(Collectors.toList());
        if (arrivalIds.isEmpty()) return BigDecimal.valueOf(50);

        List<ArrivalLine> lines = arrivalLineMapper.selectList(
                new LambdaQueryWrapper<ArrivalLine>().in(ArrivalLine::getArrivalId, arrivalIds));

        if (lines.isEmpty()) return BigDecimal.valueOf(50);

        double totalScore = 0;
        int count = 0;

        for (ArrivalLine line : lines) {
            BigDecimal orderedQty = line.getOrderedQty();
            BigDecimal arrivedQty = line.getArrivedQty();
            if (orderedQty == null || orderedQty.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal diff = arrivedQty.subtract(orderedQty).abs();
            double diffRate = diff.doubleValue() / orderedQty.doubleValue();

            double score;
            if (diffRate <= 0.02) {
                score = 100;
            } else if (diffRate <= 0.05) {
                score = 80;
            } else if (diffRate <= 0.10) {
                score = 50;
            } else {
                score = 20;
            }
            totalScore += score;
            count++;
        }

        return count > 0 ? BigDecimal.valueOf(totalScore / count)
                .setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(50);
    }

    private BigDecimal calcQcFailureRate(Long supplierId) {
        List<PurchaseOrder> pos = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getSupplierId, supplierId));
        List<Long> poIds = pos.stream().map(PurchaseOrder::getId).collect(Collectors.toList());
        if (poIds.isEmpty()) return BigDecimal.valueOf(50);

        List<Arrival> arrivals = arrivalMapper.selectList(
                new LambdaQueryWrapper<Arrival>().in(Arrival::getPoId, poIds));
        List<Long> arrivalIds = arrivals.stream().map(Arrival::getId).collect(Collectors.toList());
        if (arrivalIds.isEmpty()) return BigDecimal.valueOf(50);

        List<QualityInspection> inspections = qcMapper.selectList(
                new LambdaQueryWrapper<QualityInspection>()
                        .in(QualityInspection::getArrivalId, arrivalIds));

        if (inspections.isEmpty()) return BigDecimal.valueOf(50);

        int total = inspections.size();
        long failCount = inspections.stream().filter(i -> "FAIL".equals(i.getResult())).count();
        long condCount = inspections.stream().filter(i -> "CONDITIONAL".equals(i.getResult())).count();

        double failRate = (failCount + condCount * 0.5) / total;
        return BigDecimal.valueOf(100 * (1 - failRate)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcReturnRate(Long supplierId) {
        List<PurchaseOrder> pos = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getSupplierId, supplierId)
                        .in(PurchaseOrder::getStatus, "RECEIVED", "PARTIAL_RECEIVED", "CONFIRMED"));

        if (pos.isEmpty()) return BigDecimal.valueOf(50);

        int totalPOs = pos.size();
        List<Long> poIds = pos.stream().map(PurchaseOrder::getId).collect(Collectors.toList());

        List<ReturnOrder> returns = returnOrderMapper.selectList(
                new LambdaQueryWrapper<ReturnOrder>()
                        .in(ReturnOrder::getPoId, poIds)
                        .ne(ReturnOrder::getStatus, "REJECTED"));

        long returnedPOs = returns.stream().map(ReturnOrder::getPoId).distinct().count();
        double rate = (double) returnedPOs / totalPOs;
        double score = Math.max(0, 100 * (1 - rate * 2));

        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcReconciliationDiff(Long supplierId) {
        List<Reconciliation> recons = reconciliationMapper.selectList(
                new LambdaQueryWrapper<Reconciliation>()
                        .eq(Reconciliation::getSupplierId, supplierId));

        if (recons.isEmpty()) return BigDecimal.valueOf(50);

        double totalScore = 0;
        int count = 0;

        for (Reconciliation r : recons) {
            BigDecimal orderAmount = r.getOrderAmount();
            BigDecimal diffAmount = r.getDiffAmount();
            if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) == 0) continue;
            if (diffAmount == null) diffAmount = BigDecimal.ZERO;

            double diffRate = diffAmount.abs().doubleValue() / orderAmount.doubleValue();

            double score;
            if (diffRate <= 0.01) {
                score = 100;
            } else if (diffRate <= 0.03) {
                score = 80;
            } else if (diffRate <= 0.05) {
                score = 50;
            } else {
                score = 20;
            }
            totalScore += score;
            count++;
        }

        return count > 0 ? BigDecimal.valueOf(totalScore / count)
                .setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(50);
    }

    private BigDecimal calcApprovalAnomaly(Long supplierId) {
        List<PurchaseOrder> pos = poMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getSupplierId, supplierId));
        List<Long> poIds = pos.stream().map(PurchaseOrder::getId).collect(Collectors.toList());

        List<ReturnOrder> returns = returnOrderMapper.selectList(
                new LambdaQueryWrapper<ReturnOrder>()
                        .eq(ReturnOrder::getSupplierId, supplierId));
        List<Long> returnIds = returns.stream().map(ReturnOrder::getId).collect(Collectors.toList());

        List<Approval> allApprovals = new ArrayList<>();

        if (!poIds.isEmpty()) {
            allApprovals.addAll(approvalMapper.selectList(
                    new LambdaQueryWrapper<Approval>()
                            .eq(Approval::getBusinessType, "PO")
                            .in(Approval::getBusinessId, poIds)));
        }
        if (!returnIds.isEmpty()) {
            allApprovals.addAll(approvalMapper.selectList(
                    new LambdaQueryWrapper<Approval>()
                            .eq(Approval::getBusinessType, "RETURN")
                            .in(Approval::getBusinessId, returnIds)));
        }

        if (allApprovals.isEmpty()) return BigDecimal.valueOf(100);

        long rejected = allApprovals.stream()
                .filter(a -> "REJECTED".equals(a.getStatus())).count();
        double anomalyRate = (double) rejected / allApprovals.size();

        return BigDecimal.valueOf(100 * (1 - anomalyRate)).setScale(2, RoundingMode.HALF_UP);
    }

    // ==================== 辅助方法 ====================

    private BigDecimal addDetail(List<SupplierScoreDetail> details, String dimension,
                                  BigDecimal rawValue, BigDecimal normalizedScore, BigDecimal weight) {
        SupplierScoreDetail detail = new SupplierScoreDetail();
        detail.setDimension(dimension);
        detail.setRawValue(rawValue);
        detail.setNormalizedScore(normalizedScore);
        detail.setWeight(weight);
        BigDecimal weighted = normalizedScore.multiply(weight)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        detail.setWeightedScore(weighted);
        details.add(detail);
        return weighted;
    }

    private Map<String, BigDecimal> parseWeights(String weightsJson) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (weightsJson == null || weightsJson.isBlank()) return map;

        String cleaned = weightsJson.replace("{", "").replace("}", "").replace("\"", "");
        String[] pairs = cleaned.split(",");
        for (String pair : pairs) {
            String[] kv = pair.trim().split(":");
            if (kv.length == 2) {
                map.put(kv[0].trim(), new BigDecimal(kv[1].trim()));
            }
        }
        return map;
    }

    private int countSampleSize(Long supplierId) {
        Long count = poMapper.selectCount(
                new LambdaQueryWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getSupplierId, supplierId)
                        .in(PurchaseOrder::getStatus, "RECEIVED", "PARTIAL_RECEIVED", "CONFIRMED"));
        return count != null ? count.intValue() : 0;
    }

    private int getExpectedDeliveryDays(PurchaseOrder po) {
        if (po.getComparisonId() == null) return 30;
        Comparison comparison = comparisonMapper.selectById(po.getComparisonId());
        if (comparison == null) return 30;

        List<ComparisonLine> lines = comparisonLineMapper.selectList(
                new LambdaQueryWrapper<ComparisonLine>()
                        .eq(ComparisonLine::getComparisonId, po.getComparisonId())
                        .eq(ComparisonLine::getSupplierId, po.getSupplierId()));
        if (lines.isEmpty()) return 30;

        Integer avgDelivery = lines.get(0).getAvgDelivery();
        return avgDelivery != null ? avgDelivery : 30;
    }

    private ScoringRuleVersion getActiveRuleVersion() {
        ScoringRuleVersion rule = ruleVersionMapper.selectOne(
                new LambdaQueryWrapper<ScoringRuleVersion>()
                        .eq(ScoringRuleVersion::getStatus, "ACTIVE")
                        .orderByDesc(ScoringRuleVersion::getVersionNo)
                        .last("LIMIT 1"));
        if (rule == null) {
            throw new com.procurement.common.BusinessException("没有生效的评分规则版本");
        }
        return rule;
    }
}
