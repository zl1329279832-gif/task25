package com.procurement.module.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.order.dto.OrderCreateDTO;
import com.procurement.module.order.service.PurchaseOrderService;
import com.procurement.module.order.vo.PurchaseOrderVO;
import com.procurement.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService orderService;

    @GetMapping
    @RequireRole({"BUYER", "PURCHASE_MANAGER"})
    public Result<Page<PurchaseOrderVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(orderService.page(pageNum, pageSize, status));
    }

    @GetMapping("/{id}")
    public Result<PurchaseOrderVO> getById(@PathVariable Long id) {
        return Result.ok(orderService.getById(id));
    }

    @PostMapping
    @RequireRole("BUYER")
    public Result<Void> create(@Valid @RequestBody OrderCreateDTO dto,
                               @AuthenticationPrincipal SecurityUser user) {
        orderService.create(dto, user.getUserId());
        return Result.ok();
    }

    @PostMapping("/{id}/approve")
    @RequireRole("PURCHASE_MANAGER")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestParam boolean approved,
                                @RequestParam(required = false) String opinion,
                                @AuthenticationPrincipal SecurityUser user) {
        orderService.approve(id, approved, opinion, user.getUserId());
        return Result.ok();
    }

    @PostMapping("/{id}/confirm")
    @RequireRole("SUPPLIER")
    public Result<Void> confirm(@PathVariable Long id) {
        orderService.confirm(id);
        return Result.ok();
    }

    @PostMapping("/{id}/cancel")
    @RequireRole({"BUYER", "PURCHASE_MANAGER"})
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.ok();
    }

    @GetMapping("/supplier")
    @RequireRole("SUPPLIER")
    public Result<Page<PurchaseOrderVO>> supplierOrders(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(orderService.pageForSupplier(pageNum, pageSize, user.getSupplierId()));
    }
}
