package com.procurement.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.Result;
import com.procurement.entity.*;
import com.procurement.service.SupplierScoreService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/supplier-scores")
@RequiredArgsConstructor
public class SupplierScoreController {

    private final SupplierScoreService scoreService;

    @Data
    public static class AdjustRequest {
        private Long supplierId;
        private String dimension;
        private BigDecimal newScore;
        private String reason;
    }

    // === 查询接口 ===

    @GetMapping
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Page<SupplierScore>> list(
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(scoreService.listScores(level, page, size));
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<SupplierScore> getScore(@PathVariable Long supplierId) {
        return Result.ok(scoreService.getScore(supplierId));
    }

    @GetMapping("/{supplierId}/details")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<List<SupplierScoreDetail>> getDetails(@PathVariable Long supplierId) {
        return Result.ok(scoreService.getScoreDetails(supplierId));
    }

    @GetMapping("/snapshot/{businessType}/{businessId}")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<SupplierScoreSnapshot> getSnapshot(
            @PathVariable String businessType, @PathVariable Long businessId) {
        return Result.ok(scoreService.getSnapshot(businessType, businessId));
    }

    // === 评分计算 ===

    @PostMapping("/recalculate")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> recalculateAll() {
        scoreService.recalculateAll();
        return Result.ok();
    }

    @PostMapping("/recalculate/{supplierId}")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> recalculateSupplier(@PathVariable Long supplierId) {
        scoreService.recalculateSupplier(supplierId);
        return Result.ok();
    }

    // === 人工调整 ===

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> adjust(@RequestBody AdjustRequest req) {
        scoreService.adjustScore(req.getSupplierId(), req.getDimension(),
                req.getNewScore(), req.getReason());
        return Result.ok();
    }

    // === 规则管理 ===

    @PostMapping("/rules")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<ScoreRule> createRule(@RequestBody ScoreRule rule) {
        return Result.ok(scoreService.createRule(rule));
    }

    @PutMapping("/rules/{id}/activate")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<ScoreRule> activateRule(@PathVariable Long id) {
        return Result.ok(scoreService.activateRule(id));
    }

    @GetMapping("/rules/active")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<ScoreRule> getActiveRule() {
        return Result.ok(scoreService.getActiveRule());
    }
}
