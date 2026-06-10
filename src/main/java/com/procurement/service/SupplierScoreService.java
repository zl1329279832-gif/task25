package com.procurement.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.entity.*;

import java.math.BigDecimal;
import java.util.List;

public interface SupplierScoreService {

    // === 评分计算 ===
    void recalculateAll();
    void recalculateSupplier(Long supplierId);

    // === 规则管理 ===
    ScoreRule createRule(ScoreRule rule);
    ScoreRule activateRule(Long ruleId);
    ScoreRule getActiveRule();

    // === 人工调整 ===
    void adjustScore(Long supplierId, String dimension, BigDecimal newScore, String reason);

    // === 查询 ===
    SupplierScore getScore(Long supplierId);
    Page<SupplierScore> listScores(String level, int page, int size);
    List<SupplierScoreDetail> getScoreDetails(Long supplierId);
    SupplierScoreSnapshot getSnapshot(String businessType, Long businessId);

    // === 准入校验 ===
    void checkSupplierAccess(Long supplierId, String operation);
    SupplierScoreSnapshot createSnapshot(Long supplierId, String businessType, Long businessId);
}
