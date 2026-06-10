package com.procurement.module.reconciliation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.reconciliation.service.ReconciliationService;
import com.procurement.module.reconciliation.vo.ReconciliationVO;
import com.procurement.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reconciliations")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @GetMapping
    @RequireRole({"FINANCE", "BUYER"})
    public Result<Page<ReconciliationVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status) {
        return Result.ok(reconciliationService.page(pageNum, pageSize, supplierId, status));
    }

    @GetMapping("/{id}")
    @RequireRole({"FINANCE", "BUYER"})
    public Result<ReconciliationVO> getById(@PathVariable Long id) {
        return Result.ok(reconciliationService.getById(id));
    }

    @PostMapping("/generate")
    @RequireRole("FINANCE")
    public Result<Void> generate(
            @RequestParam Long supplierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @AuthenticationPrincipal SecurityUser user) {
        reconciliationService.generate(supplierId, periodStart, periodEnd, user.getUserId());
        return Result.ok();
    }

    @PostMapping("/{id}/confirm")
    @RequireRole("FINANCE")
    public Result<Void> confirm(@PathVariable Long id) {
        reconciliationService.confirm(id);
        return Result.ok();
    }

    @PostMapping("/{id}/dispute")
    @RequireRole({"FINANCE", "SUPPLIER"})
    public Result<Void> dispute(@PathVariable Long id, @RequestParam String reason) {
        reconciliationService.dispute(id, reason);
        return Result.ok();
    }

    @PostMapping("/{id}/settle")
    @RequireRole("FINANCE")
    public Result<Void> settle(@PathVariable Long id) {
        reconciliationService.settle(id);
        return Result.ok();
    }
}
