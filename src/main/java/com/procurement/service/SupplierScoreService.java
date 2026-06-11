package com.procurement.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.entity.ScoringRuleVersion;
import com.procurement.entity.SupplierScore;
import com.procurement.entity.SupplierScoreAdjustment;
import com.procurement.entity.SupplierScoreDetail;
import com.procurement.entity.SupplierScoreSnapshot;

import java.math.BigDecimal;
import java.util.List;

public interface SupplierScoreService {

    SupplierScore calculateScore(Long supplierId);

    void recalculateAll();

    SupplierScore getCurrentScore(Long supplierId);

    List<SupplierScoreDetail> getScoreDetails(Long supplierScoreId);

    SupplierScore adjustScore(Long supplierId, BigDecimal newScore, String reason);

    Page<SupplierScoreAdjustment> getAdjustmentHistory(Long supplierId, int page, int size);

    SupplierScoreSnapshot getPoSnapshot(Long poId);

    SupplierScoreSnapshot createSnapshot(Long poId, Long supplierId);

    SupplierScoreSnapshot createQuoteSnapshot(Long quoteId, Long supplierId);

    ScoringRuleVersion createRuleVersion(String weights, String thresholds);

    ScoringRuleVersion getActiveRuleVersion();

    Page<ScoringRuleVersion> listRuleVersions(int page, int size);

    Page<SupplierScore> listAllScores(int page, int size);
}
