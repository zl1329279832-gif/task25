package com.procurement.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.BusinessException;
import com.procurement.common.Result;
import com.procurement.entity.PurchaseOrder;
import com.procurement.entity.PurchaseOrderLine;
import com.procurement.security.LoginUser;
import com.procurement.service.PurchaseOrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService poService;

    @Data
    public static class CreatePORequest {
        private Long supplierId;
        private Long comparisonId;
        private List<PurchaseOrderLine> lines;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<PurchaseOrder> create(@RequestBody CreatePORequest req) {
        PurchaseOrder po = new PurchaseOrder();
        po.setSupplierId(req.getSupplierId());
        po.setComparisonId(req.getComparisonId());
        return Result.ok(poService.create(po, req.getLines()));
    }

    @PutMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Void> submit(@PathVariable Long id) {
        poService.submitForApproval(id);
        return Result.ok();
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> approve(@PathVariable Long id) {
        LoginUser user = getCurrentUser();
        poService.approve(id, user.getUserId());
        return Result.ok();
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) String comment) {
        LoginUser user = getCurrentUser();
        poService.reject(id, user.getUserId(), comment);
        return Result.ok();
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Void> confirm(@PathVariable Long id) {
        poService.confirm(id);
        return Result.ok();
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam String reason) {
        poService.cancel(id, reason);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<PurchaseOrder> getById(@PathVariable Long id) {
        PurchaseOrder po = poService.getById(id);
        LoginUser user = getCurrentUser();
        if ("SUPPLIER".equals(user.getRole()) && !user.getSupplierId().equals(po.getSupplierId())) {
            throw new BusinessException("无权查看其他供应商的订单");
        }
        return Result.ok(po);
    }

    @GetMapping("/{id}/lines")
    public Result<List<PurchaseOrderLine>> getLines(@PathVariable Long id) {
        return Result.ok(poService.getLines(id));
    }

    @GetMapping
    public Result<Page<PurchaseOrder>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        LoginUser user = getCurrentUser();
        if ("SUPPLIER".equals(user.getRole())) {
            supplierId = user.getSupplierId();
        }
        return Result.ok(poService.list(status, supplierId, page, size));
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
