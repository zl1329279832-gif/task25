package com.procurement.controller;

import com.procurement.common.Result;
import com.procurement.entity.Reconciliation;
import com.procurement.entity.ReconciliationLine;
import com.procurement.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
        return Result.ok(reconService.getById(id));
    }

    @GetMapping("/{id}/lines")
    public Result<List<ReconciliationLine>> getLines(@PathVariable Long id) {
        return Result.ok(reconService.getLines(id));
    }

    @GetMapping
    public Result<List<Reconciliation>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reconService.list(status, page, size));
    }
}
