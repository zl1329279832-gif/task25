package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.service.QuoteService;
import com.procurement.service.SupplierScoreService;
import com.procurement.state.QuoteStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {

    private final QuoteMapper quoteMapper;
    private final QuoteLineMapper quoteLineMapper;
    private final RfqMapper rfqMapper;
    private final SupplierScoreMapper supplierScoreMapper;
    private final ScoringRuleVersionMapper ruleVersionMapper;
    private final SupplierScoreService supplierScoreService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUOTE_LOCK_PREFIX = "quote:lock:";

    @Override
    @Transactional
    @Auditable(action = "SUBMIT_QUOTE", entityType = "Quote")
    public Quote submitQuote(Long rfqId, Long supplierId, List<QuoteLine> lines) {
        // 使用 Redis 分布式锁防止并发问题 — 先获取锁再检查状态
        String lockKey = QUOTE_LOCK_PREFIX + rfqId + ":" + supplierId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            throw new BusinessException("操作过于频繁，请稍后重试");
        }

        try {
            // 锁内重新获取 RFQ，确保状态和截止时间的原子性检查
            Rfq rfq = rfqMapper.selectById(rfqId);
            if (rfq == null) throw new BusinessException("询价单不存在");

            RfqStatus rfqStatus = RfqStatus.valueOf(rfq.getStatus());
            if (rfqStatus == RfqStatus.CLOSED || rfqStatus == RfqStatus.CANCELLED) {
                throw new BusinessException("询价单已关闭或已取消，无法提交报价");
            }
            if (LocalDateTime.now().isAfter(rfq.getDeadline())) {
                throw new BusinessException("报价已截止，无法提交或修改");
            }

            // 查找现有报价，确定版本号
            Quote existing = quoteMapper.selectOne(
                    new LambdaQueryWrapper<Quote>()
                            .eq(Quote::getRfqId, rfqId)
                            .eq(Quote::getSupplierId, supplierId)
                            .orderByDesc(Quote::getVersion)
                            .last("LIMIT 1"));

            Quote quote;
            if (existing != null) {
                // 检查是否被冻结
                if (existing.getFrozen() == 1) {
                    throw new BusinessException("报价已被冻结，无法修改");
                }
                // 创建新版本
                quote = new Quote();
                quote.setQuoteNo(existing.getQuoteNo());
                quote.setRfqId(rfqId);
                quote.setSupplierId(supplierId);
                quote.setVersion(existing.getVersion() + 1);
            } else {
                quote = new Quote();
                quote.setQuoteNo("QT-" + System.currentTimeMillis());
                quote.setRfqId(rfqId);
                quote.setSupplierId(supplierId);
                quote.setVersion(1);
            }

            // 计算总金额
            BigDecimal total = BigDecimal.ZERO;
            for (QuoteLine line : lines) {
                total = total.add(line.getUnitPrice().multiply(line.getQuantity()));
            }

            quote.setTotalAmount(total);
            quote.setStatus(QuoteStatus.SUBMITTED.name());
            quote.setFrozen(0);
            quote.setSubmittedAt(LocalDateTime.now());
            quoteMapper.insert(quote);

            // 插入行项
            for (QuoteLine line : lines) {
                line.setQuoteId(quote.getId());
                quoteLineMapper.insert(line);
            }

            return quote;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    @Transactional
    @Auditable(action = "FREEZE_QUOTE", entityType = "Quote")
    public void freezeQuote(Long quoteId) {
        Quote quote = quoteMapper.selectById(quoteId);
        if (quote == null) throw new BusinessException("报价单不存在");
        QuoteStateMachine.validateTransition(QuoteStatus.valueOf(quote.getStatus()), QuoteStatus.FROZEN);

        // 捕获冻结时供应商评分
        captureScoreAtFreeze(quote);

        quote.setFrozen(1);
        quote.setStatus(QuoteStatus.FROZEN.name());
        quoteMapper.updateById(quote);

        // 创建冻结快照
        try {
            supplierScoreService.createQuoteSnapshot(quoteId, quote.getSupplierId());
        } catch (Exception e) {
            // 快照创建失败不阻断冻结操作（非事务关键路径）
        }
    }

    @Override
    @Transactional
    public void freezeAllByRfq(Long rfqId) {
        List<Quote> quotes = quoteMapper.selectList(
                new LambdaQueryWrapper<Quote>()
                        .eq(Quote::getRfqId, rfqId)
                        .eq(Quote::getFrozen, 0));
        for (Quote q : quotes) {
            // 捕获冻结时供应商评分
            captureScoreAtFreeze(q);

            q.setFrozen(1);
            q.setStatus(QuoteStatus.FROZEN.name());
            quoteMapper.updateById(q);

            // 创建冻结快照
            try {
                supplierScoreService.createQuoteSnapshot(q.getId(), q.getSupplierId());
            } catch (Exception e) {
                // 快照创建失败不阻断冻结操作
            }
        }
    }

    @Override
    public Quote getLatestQuote(Long rfqId, Long supplierId) {
        return quoteMapper.selectOne(
                new LambdaQueryWrapper<Quote>()
                        .eq(Quote::getRfqId, rfqId)
                        .eq(Quote::getSupplierId, supplierId)
                        .orderByDesc(Quote::getVersion)
                        .last("LIMIT 1"));
    }

    @Override
    public List<Quote> getAllQuotesByRfq(Long rfqId) {
        // 获取每个供应商的最新版本
        return quoteMapper.selectList(
                new LambdaQueryWrapper<Quote>()
                        .eq(Quote::getRfqId, rfqId)
                        .orderByDesc(Quote::getVersion));
    }

    @Override
    public List<QuoteLine> getQuoteLines(Long quoteId) {
        return quoteLineMapper.selectList(
                new LambdaQueryWrapper<QuoteLine>().eq(QuoteLine::getQuoteId, quoteId));
    }

    /**
     * 捕获冻结时的供应商评分和规则版本号
     */
    private void captureScoreAtFreeze(Quote quote) {
        SupplierScore score = supplierScoreMapper.selectOne(
                new LambdaQueryWrapper<SupplierScore>()
                        .eq(SupplierScore::getSupplierId, quote.getSupplierId()));
        quote.setScoreAtFreeze(score != null ? score.getTotalScore() : BigDecimal.valueOf(50));

        ScoringRuleVersion rule = ruleVersionMapper.selectOne(
                new LambdaQueryWrapper<ScoringRuleVersion>()
                        .eq(ScoringRuleVersion::getStatus, "ACTIVE")
                        .orderByDesc(ScoringRuleVersion::getVersionNo)
                        .last("LIMIT 1"));
        quote.setScoreRuleVersionAtFreeze(rule != null ? rule.getVersionNo() : null);
    }
}
