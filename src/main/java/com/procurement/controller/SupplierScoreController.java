package com.procurement.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.Result;
import com.procurement.entity.*;
import com.procurement.service.AdmissionControlService;
import com.procurement.service.SupplierScoreService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/supplier-scores")
@RequiredArgsConstructor
public class SupplierScoreController {

    private final SupplierScoreService scoreService;
    private final AdmissionControlService admissionControlService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Page<SupplierScore>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 简单分页查询所有供应商评分
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SupplierScore> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.orderByDesc(SupplierScore::getTotalScore);
        // 使用 MyBatis Plus 分页
        Page<SupplierScore> result = new Page<>(page, size);
        // 需要通过mapper查询，但这里简化为通过service获取
        return Result.ok(scoreService.listAllScores(page, size));
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Map<String, Object>> getScore(@PathVariable Long supplierId) {
        SupplierScore score = scoreService.getCurrentScore(supplierId);
        Map<String, Object> result = new HashMap<>();
        result.put("score", score);
        if (score != null) {
            result.put("details", scoreService.getScoreDetails(score.getId()));
        }
        return Result.ok(result);
    }

    @GetMapping("/{supplierId}/history")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Page<SupplierScoreAdjustment>> getHistory(
            @PathVariable Long supplierId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(scoreService.getAdjustmentHistory(supplierId, page, size));
    }

    @PutMapping("/{supplierId}/adjust")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<SupplierScore> adjustScore(
            @PathVariable Long supplierId,
            @RequestBody AdjustScoreRequest req) {
        return Result.ok(scoreService.adjustScore(supplierId, req.getNewScore(), req.getReason()));
    }

    @PostMapping("/recalculate")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> recalculate() {
        scoreService.recalculateAll();
        return Result.ok();
    }

    @PostMapping("/rules")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<ScoringRuleVersion> createRule(@RequestBody CreateRuleRequest req) {
        return Result.ok(scoreService.createRuleVersion(req.getWeights(), req.getThresholds()));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Page<ScoringRuleVersion>> listRules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(scoreService.listRuleVersions(page, size));
    }

    @GetMapping("/admission-logs/{supplierId}")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Page<SupplierAdmissionLog>> getAdmissionLogs(
            @PathVariable Long supplierId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(admissionControlService.getAdmissionLogs(supplierId, page, size));
    }

    @GetMapping("/po-snapshot/{poId}")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER','SUPPLIER')")
    public Result<SupplierScoreSnapshot> getPoSnapshot(@PathVariable Long poId) {
        return Result.ok(scoreService.getPoSnapshot(poId));
    }

    @Data
    static class AdjustScoreRequest {
        private BigDecimal newScore;
        private String reason;
    }

    @Data
    static class CreateRuleRequest {
        private String weights;
        private String thresholds;
    }
}
