package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.ComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComparisonServiceImpl implements ComparisonService {

    private final ComparisonMapper comparisonMapper;
    private final ComparisonLineMapper comparisonLineMapper;
    private final QuoteMapper quoteMapper;
    private final QuoteLineMapper quoteLineMapper;
    private final RfqMapper rfqMapper;

    @Override
    @Transactional
    @Auditable(action = "CREATE_COMPARISON", entityType = "Comparison")
    public Comparison createComparison(Long rfqId, String rule) {
        Rfq rfq = rfqMapper.selectById(rfqId);
        if (rfq == null) throw new BusinessException("询价单不存在");

        // 获取所有报价（每个供应商取最新版本）
        List<Quote> allQuotes = quoteMapper.selectList(
                new LambdaQueryWrapper<Quote>()
                        .eq(Quote::getRfqId, rfqId)
                        .eq(Quote::getStatus, QuoteStatus.SUBMITTED.name())
                        .or().eq(Quote::getStatus, QuoteStatus.FROZEN.name()));

        // 按供应商分组，取最高版本
        Map<Long, Quote> latestBySupplier = new HashMap<>();
        for (Quote q : allQuotes) {
            latestBySupplier.merge(q.getSupplierId(), q,
                    (a, b) -> a.getVersion() > b.getVersion() ? a : b);
        }

        if (latestBySupplier.isEmpty()) {
            throw new BusinessException("没有可比较的报价");
        }

        LoginUser user = getCurrentUser();
        Comparison comparison = new Comparison();
        comparison.setComparisonNo("CMP-" + System.currentTimeMillis());
        comparison.setRfqId(rfqId);
        comparison.setRule(rule);
        comparison.setStatus("PENDING");
        comparison.setCreatedBy(user.getUserId());
        comparisonMapper.insert(comparison);

        // 为每个供应商创建比价行
        List<ComparisonLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Quote> entry : latestBySupplier.entrySet()) {
            Quote quote = entry.getValue();
            ComparisonLine line = new ComparisonLine();
            line.setComparisonId(comparison.getId());
            line.setQuoteId(quote.getId());
            line.setSupplierId(quote.getSupplierId());
            line.setTotalAmount(quote.getTotalAmount());

            // 计算平均交期
            List<QuoteLine> quoteLines = quoteLineMapper.selectList(
                    new LambdaQueryWrapper<QuoteLine>().eq(QuoteLine::getQuoteId, quote.getId()));
            if (!quoteLines.isEmpty()) {
                double avgDays = quoteLines.stream()
                        .filter(ql -> ql.getDeliveryDays() != null)
                        .mapToInt(QuoteLine::getDeliveryDays)
                        .average()
                        .orElse(0);
                line.setAvgDelivery((int) avgDays);
            }

            lines.add(line);
        }

        // 根据规则计算评分和排名
        calculateScores(lines, rule);

        for (ComparisonLine line : lines) {
            comparisonLineMapper.insert(line);
        }

        comparison.setStatus("COMPLETED");
        comparison.setResultSummary(buildSummary(lines, rule));
        comparisonMapper.updateById(comparison);

        return comparison;
    }

    @Override
    @Auditable(action = "SELECT_SUPPLIER", entityType = "Comparison")
    public void selectSupplier(Long comparisonId, Long quoteId) {
        List<ComparisonLine> lines = comparisonLineMapper.selectList(
                new LambdaQueryWrapper<ComparisonLine>()
                        .eq(ComparisonLine::getComparisonId, comparisonId));
        for (ComparisonLine line : lines) {
            line.setSelected(line.getQuoteId().equals(quoteId) ? 1 : 0);
            comparisonLineMapper.updateById(line);
        }
    }

    @Override
    @Auditable(action = "APPROVE_COMPARISON", entityType = "Comparison")
    public void approve(Long comparisonId) {
        Comparison c = comparisonMapper.selectById(comparisonId);
        if (c == null) throw new BusinessException("比价单不存在");
        c.setStatus("APPROVED");
        comparisonMapper.updateById(c);
    }

    @Override
    public Comparison getById(Long id) {
        Comparison c = comparisonMapper.selectById(id);
        if (c == null) throw new BusinessException("比价单不存在");
        return c;
    }

    @Override
    public List<ComparisonLine> getLines(Long comparisonId) {
        return comparisonLineMapper.selectList(
                new LambdaQueryWrapper<ComparisonLine>()
                        .eq(ComparisonLine::getComparisonId, comparisonId)
                        .orderByAsc(ComparisonLine::getRankNo));
    }

    private void calculateScores(List<ComparisonLine> lines, String rule) {
        if ("LOWEST_PRICE".equals(rule)) {
            // 按价格排序
            lines.sort(Comparator.comparing(ComparisonLine::getTotalAmount));
            for (int i = 0; i < lines.size(); i++) {
                lines.get(i).setRankNo(i + 1);
                // 最低价100分，其他按比例
                BigDecimal base = lines.get(0).getTotalAmount();
                BigDecimal current = lines.get(i).getTotalAmount();
                BigDecimal score = current.compareTo(BigDecimal.ZERO) > 0
                        ? BigDecimal.valueOf(100).multiply(base).divide(current, 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                lines.get(i).setScore(score);
            }
        } else {
            // COMPREHENSIVE: 价格60% + 交期40%
            BigDecimal minPrice = lines.stream()
                    .map(ComparisonLine::getTotalAmount)
                    .min(Comparator.naturalOrder())
                    .orElse(BigDecimal.ONE);
            int minDays = lines.stream()
                    .filter(l -> l.getAvgDelivery() != null)
                    .mapToInt(ComparisonLine::getAvgDelivery)
                    .min().orElse(1);

            for (ComparisonLine line : lines) {
                BigDecimal priceScore = BigDecimal.valueOf(60)
                        .multiply(minPrice)
                        .divide(line.getTotalAmount(), 2, java.math.RoundingMode.HALF_UP);
                BigDecimal deliveryScore = BigDecimal.valueOf(40 * minDays)
                        .divide(BigDecimal.valueOf(Math.max(line.getAvgDelivery(), 1)),
                                2, java.math.RoundingMode.HALF_UP);
                line.setScore(priceScore.add(deliveryScore));
            }
            lines.sort(Comparator.comparing(ComparisonLine::getScore).reversed());
            for (int i = 0; i < lines.size(); i++) {
                lines.get(i).setRankNo(i + 1);
            }
        }
    }

    private String buildSummary(List<ComparisonLine> lines, String rule) {
        if (lines.isEmpty()) return "无比价数据";
        ComparisonLine best = lines.get(0);
        return String.format("规则: %s, 推荐供应商ID: %d, 金额: %s, 评分: %s",
                rule, best.getSupplierId(), best.getTotalAmount(), best.getScore());
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
