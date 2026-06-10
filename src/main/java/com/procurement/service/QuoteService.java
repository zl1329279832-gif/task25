package com.procurement.service;

import com.procurement.entity.Quote;
import com.procurement.entity.QuoteLine;
import java.util.List;

public interface QuoteService {
    /**
     * 创建或更新报价（自动递增版本号）
     * 如果报价截止则不允许修改
     */
    Quote submitQuote(Long rfqId, Long supplierId, List<QuoteLine> lines);

    /**
     * 冻结报价（截止后自动调用）
     */
    void freezeQuote(Long quoteId);

    /**
     * 冻结某询价单下所有报价（截止时调用）
     */
    void freezeAllByRfq(Long rfqId);

    /**
     * 获取某供应商对某询价单的最新版本报价
     */
    Quote getLatestQuote(Long rfqId, Long supplierId);

    /**
     * 获取某询价单的所有报价（各供应商最新版本）
     */
    List<Quote> getAllQuotesByRfq(Long rfqId);

    /**
     * 获取报价行项
     */
    List<QuoteLine> getQuoteLines(Long quoteId);
}
