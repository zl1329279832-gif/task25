package com.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.audit.Auditable;
import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {

    private final QuoteMapper quoteMapper;
    private final QuoteLineMapper quoteLineMapper;
    private final RfqMapper rfqMapper;
    private final RfqSupplierMapper rfqSupplierMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUOTE_LOCK_PREFIX = "quote:lock:";

    @Override
    @Transactional
    @Auditable(action = "SUBMIT_QUOTE", entityType = "Quote")
    public Quote submitQuote(Long rfqId, Long supplierId, List<QuoteLine> lines) {
        // 供应商权限隔离：校验当前用户只能为自己的供应商身份报价
        validateSupplierAccess(supplierId);
        Rfq rfq = rfqMapper.selectById(rfqId);
        if (rfq == null) throw new BusinessException("询价单不存在");

        // 校验 RFQ 状态：只有 PUBLISHED 状态才允许报价
        RfqStatus rfqStatus = RfqStatus.valueOf(rfq.getStatus());
        if (rfqStatus != RfqStatus.PUBLISHED) {
            throw new BusinessException("询价单状态不允许报价: " + rfqStatus);
        }

        // 检查是否已过截止时间
        if (LocalDateTime.now().isAfter(rfq.getDeadline())) {
            throw new BusinessException("报价已截止，无法提交或修改");
        }

        // 校验供应商是否被邀请参与此询价
        Long invitationCount = rfqSupplierMapper.selectCount(
                new LambdaQueryWrapper<RfqSupplier>()
                        .eq(RfqSupplier::getRfqId, rfqId)
                        .eq(RfqSupplier::getSupplierId, supplierId));
        if (invitationCount == 0) {
            throw new BusinessException("供应商未被邀请参与此询价");
        }

        // 使用 Redis 分布式锁防止并发问题
        String lockKey = QUOTE_LOCK_PREFIX + rfqId + ":" + supplierId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            throw new BusinessException("操作过于频繁，请稍后重试");
        }

        try {
            // 查找现有报价，确定版本号
            Quote existing = quoteMapper.selectOne(
                    new LambdaQueryWrapper<Quote>()
                            .eq(Quote::getRfqId, rfqId)
                            .eq(Quote::getSupplierId, supplierId)
                            .orderByDesc(Quote::getVersion)
                            .last("LIMIT 1"));

            Quote quote;
            int expectedVersion;
            if (existing != null) {
                // 检查是否被冻结
                if (existing.getFrozen() == 1) {
                    throw new BusinessException("报价已被冻结，无法修改");
                }
                expectedVersion = existing.getVersion() + 1;
                // 创建新版本
                quote = new Quote();
                quote.setQuoteNo(existing.getQuoteNo());
                quote.setRfqId(rfqId);
                quote.setSupplierId(supplierId);
                quote.setVersion(expectedVersion);
            } else {
                expectedVersion = 1;
                quote = new Quote();
                quote.setQuoteNo("QT-" + System.currentTimeMillis());
                quote.setRfqId(rfqId);
                quote.setSupplierId(supplierId);
                quote.setVersion(expectedVersion);
            }

            // 幂等校验：检查该版本是否已存在（防止重复提交）
            Long duplicateCount = quoteMapper.selectCount(
                    new LambdaQueryWrapper<Quote>()
                            .eq(Quote::getRfqId, rfqId)
                            .eq(Quote::getSupplierId, supplierId)
                            .eq(Quote::getVersion, expectedVersion));
            if (duplicateCount > 0) {
                throw new BusinessException("该版本报价已存在，请勿重复提交");
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
    @Auditable(action = "FREEZE_QUOTE", entityType = "Quote")
    public void freezeQuote(Long quoteId) {
        Quote quote = quoteMapper.selectById(quoteId);
        if (quote == null) throw new BusinessException("报价单不存在");
        quote.setFrozen(1);
        quote.setStatus(QuoteStatus.FROZEN.name());
        quoteMapper.updateById(quote);
    }

    @Override
    @Transactional
    public void freezeAllByRfq(Long rfqId) {
        List<Quote> quotes = quoteMapper.selectList(
                new LambdaQueryWrapper<Quote>()
                        .eq(Quote::getRfqId, rfqId)
                        .eq(Quote::getFrozen, 0));
        for (Quote q : quotes) {
            q.setFrozen(1);
            q.setStatus(QuoteStatus.FROZEN.name());
            quoteMapper.updateById(q);
        }
    }

    @Override
    public Quote getLatestQuote(Long rfqId, Long supplierId) {
        // 供应商权限隔离
        validateSupplierAccess(supplierId);
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
        LambdaQueryWrapper<Quote> wrapper = new LambdaQueryWrapper<Quote>()
                .eq(Quote::getRfqId, rfqId)
                .orderByDesc(Quote::getVersion);

        // 供应商只能看到自己的报价
        LoginUser currentUser = getCurrentUser();
        if (currentUser != null && "SUPPLIER".equals(currentUser.getRole())) {
            wrapper.eq(Quote::getSupplierId, currentUser.getSupplierId());
        }

        return quoteMapper.selectList(wrapper);
    }

    @Override
    public List<QuoteLine> getQuoteLines(Long quoteId) {
        // 供应商权限隔离：验证报价属于自己
        LoginUser currentUser = getCurrentUser();
        if (currentUser != null && "SUPPLIER".equals(currentUser.getRole())) {
            Quote quote = quoteMapper.selectById(quoteId);
            if (quote != null && !quote.getSupplierId().equals(currentUser.getSupplierId())) {
                throw new BusinessException("无权访问此报价");
            }
        }
        return quoteLineMapper.selectList(
                new LambdaQueryWrapper<QuoteLine>().eq(QuoteLine::getQuoteId, quoteId));
    }

    /**
     * 供应商权限隔离：如果当前用户是供应商角色，校验只能操作自己的数据
     */
    private void validateSupplierAccess(Long supplierId) {
        LoginUser currentUser = getCurrentUser();
        if (currentUser != null && "SUPPLIER".equals(currentUser.getRole())) {
            if (!supplierId.equals(currentUser.getSupplierId())) {
                throw new BusinessException("无权操作其他供应商的数据");
            }
        }
    }

    private LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return (LoginUser) auth.getPrincipal();
        }
        return null;
    }
}
