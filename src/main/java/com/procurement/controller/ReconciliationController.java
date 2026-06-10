package com.procurement.controller;

import com.procurement.common.BusinessException;
import com.procurement.common.Result;
import com.procurement.entity.Reconciliation;
import com.procurement.entity.ReconciliationLine;
import com.procurement.security.LoginUser;
import com.procurement.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reconciliations")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconService;

    @PostMapping("/generate/{poId}")
    @PreAuthorize("hasRole('FINANCE')")
    public Result<Reconciliation> generate(@PathVariable Long poId) {
        return Result.ok(reconService.generate(poId));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('FINANCE')")
    public Result<Void> approve(@PathVariable Long id) {
        reconService.approve(id);
        return Result.ok();
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('FINANCE')")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        reconService.reject(id, body.get("remark"));
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<Reconciliation> getById(@PathVariable Long id) {
        Reconciliation recon = reconService.getById(id);
        LoginUser user = getCurrentUser();
        if ("SUPPLIER".equals(user.getRole()) && !user.getSupplierId().equals(recon.getSupplierId())) {
            throw new BusinessException("无权查看其他供应商的对账单");
        }
        return Result.ok(recon);
    }

    @GetMapping("/{id}/lines")
    public Result<List<ReconciliationLine>> getLines(@PathVariable Long id) {
        return Result.ok(reconService.getLines(id));
    }

    @GetMapping
    public Result<List<Reconciliation>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        LoginUser user = getCurrentUser();
        if ("SUPPLIER".equals(user.getRole())) {
            supplierId = user.getSupplierId();
        }
        return Result.ok(reconService.list(status, supplierId, page, size));
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
