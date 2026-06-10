package com.procurement.module.returns.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.returns.dto.ReturnCreateDTO;
import com.procurement.module.returns.service.ReturnOrderService;
import com.procurement.module.returns.vo.ReturnOrderVO;
import com.procurement.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnOrderController {

    private final ReturnOrderService returnOrderService;

    @GetMapping
    @RequireRole({"WAREHOUSE", "BUYER"})
    public Result<Page<ReturnOrderVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long orderId) {
        return Result.ok(returnOrderService.page(pageNum, pageSize, orderId));
    }

    @GetMapping("/{id}")
    @RequireRole({"WAREHOUSE", "BUYER", "SUPPLIER"})
    public Result<ReturnOrderVO> getById(@PathVariable Long id) {
        return Result.ok(returnOrderService.getById(id));
    }

    @PostMapping
    @RequireRole("WAREHOUSE")
    public Result<Void> create(@Valid @RequestBody ReturnCreateDTO dto,
                               @AuthenticationPrincipal SecurityUser user) {
        returnOrderService.create(dto, user.getUserId());
        return Result.ok();
    }

    @PostMapping("/{id}/supplier-confirm")
    @RequireRole("SUPPLIER")
    public Result<Void> supplierConfirm(@PathVariable Long id) {
        returnOrderService.supplierConfirm(id);
        return Result.ok();
    }

    @PostMapping("/{id}/ship")
    @RequireRole("WAREHOUSE")
    public Result<Void> ship(@PathVariable Long id) {
        returnOrderService.ship(id);
        return Result.ok();
    }

    @PostMapping("/{id}/complete")
    @RequireRole("WAREHOUSE")
    public Result<Void> complete(@PathVariable Long id) {
        returnOrderService.complete(id);
        return Result.ok();
    }
}
