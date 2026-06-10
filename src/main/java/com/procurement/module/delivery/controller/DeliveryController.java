package com.procurement.module.delivery.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.delivery.dto.DeliveryCreateDTO;
import com.procurement.module.delivery.service.DeliveryService;
import com.procurement.module.delivery.vo.DeliveryVO;
import com.procurement.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    @RequireRole({"WAREHOUSE", "BUYER"})
    public Result<Page<DeliveryVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long orderId) {
        return Result.ok(deliveryService.page(pageNum, pageSize, orderId));
    }

    @GetMapping("/{id}")
    @RequireRole({"WAREHOUSE", "BUYER"})
    public Result<DeliveryVO> getById(@PathVariable Long id) {
        return Result.ok(deliveryService.getById(id));
    }

    @PostMapping
    @RequireRole("WAREHOUSE")
    public Result<Void> create(@Valid @RequestBody DeliveryCreateDTO dto,
                               @AuthenticationPrincipal SecurityUser user) {
        deliveryService.create(dto, user.getUserId());
        return Result.ok();
    }

    @PostMapping("/{id}/receive")
    @RequireRole("WAREHOUSE")
    public Result<Void> receive(@PathVariable Long id,
                                @AuthenticationPrincipal SecurityUser user) {
        deliveryService.receive(id, user.getUserId());
        return Result.ok();
    }

    @GetMapping("/order/{orderId}")
    @RequireRole({"WAREHOUSE", "BUYER"})
    public Result<Page<DeliveryVO>> byOrder(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(deliveryService.page(pageNum, pageSize, orderId));
    }
}
