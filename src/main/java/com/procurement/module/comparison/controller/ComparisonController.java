package com.procurement.module.comparison.controller;

import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.comparison.service.ComparisonService;
import com.procurement.module.comparison.vo.ComparisonResultVO;
import com.procurement.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comparisons")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;

    @PostMapping
    @RequireRole({"BUYER", "PURCHASE_MANAGER"})
    public Result<ComparisonResultVO> compare(
            @RequestParam Long inquiryId,
            @RequestParam(defaultValue = "LOWEST_PRICE") String type,
            @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(comparisonService.compare(inquiryId, type, user.getUserId()));
    }

    @GetMapping("/{id}")
    @RequireRole({"BUYER", "PURCHASE_MANAGER"})
    public Result<ComparisonResultVO> getById(@PathVariable Long id) {
        return Result.ok(comparisonService.getById(id));
    }
}
