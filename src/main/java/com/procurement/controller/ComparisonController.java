package com.procurement.controller;

import com.procurement.common.Result;
import com.procurement.entity.Comparison;
import com.procurement.entity.ComparisonLine;
import com.procurement.service.ComparisonService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comparisons")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;

    @Data
    public static class CreateComparisonRequest {
        private Long rfqId;
        private String rule; // LOWEST_PRICE / COMPREHENSIVE
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Comparison> create(@RequestBody CreateComparisonRequest req) {
        return Result.ok(comparisonService.createComparison(req.getRfqId(), req.getRule()));
    }

    @PutMapping("/{id}/select")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Void> selectSupplier(@PathVariable Long id, @RequestParam Long quoteId) {
        comparisonService.selectSupplier(id, quoteId);
        return Result.ok();
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> approve(@PathVariable Long id) {
        comparisonService.approve(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<Comparison> getById(@PathVariable Long id) {
        return Result.ok(comparisonService.getById(id));
    }

    @GetMapping("/{id}/lines")
    public Result<List<ComparisonLine>> getLines(@PathVariable Long id) {
        return Result.ok(comparisonService.getLines(id));
    }
}
