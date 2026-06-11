package com.procurement.service;

import com.procurement.entity.SupplierScore;

/**
 * 单供应商评分计算服务 — 每个供应商在独立事务中完成评分重算，
 * 避免 recalculateAll 长事务导致的行锁争用和并发准入检查读到部分更新状态。
 */
public interface ScoreCalculationService {

    /**
     * 在独立新事务中计算单个供应商的综合评分。
     * 使用 REQUIRES_NEW 传播级别，失败不影响其他供应商。
     */
    SupplierScore calculateSingleScore(Long supplierId);
}
