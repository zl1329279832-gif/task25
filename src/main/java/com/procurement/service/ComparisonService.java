package com.procurement.service;

import com.procurement.entity.Comparison;
import com.procurement.entity.ComparisonLine;
import java.util.List;

public interface ComparisonService {
    /**
     * 自动比价：根据规则（最低价/综合评分）对比各报价
     */
    Comparison createComparison(Long rfqId, String rule);

    /**
     * 选定供应商
     */
    void selectSupplier(Long comparisonId, Long quoteId);

    /**
     * 审批比价结果
     */
    void approve(Long comparisonId);

    Comparison getById(Long id);
    List<ComparisonLine> getLines(Long comparisonId);
}
