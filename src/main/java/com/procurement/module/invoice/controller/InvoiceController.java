package com.procurement.module.invoice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.invoice.dto.InvoiceCreateDTO;
import com.procurement.module.invoice.service.InvoiceService;
import com.procurement.module.invoice.vo.InvoiceVO;
import com.procurement.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @RequireRole({"FINANCE", "BUYER"})
    public Result<Page<InvoiceVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String status) {
        return Result.ok(invoiceService.page(pageNum, pageSize, orderId, status));
    }

    @GetMapping("/{id}")
    @RequireRole({"FINANCE", "BUYER"})
    public Result<InvoiceVO> getById(@PathVariable Long id) {
        return Result.ok(invoiceService.getById(id));
    }

    @PostMapping
    @RequireRole("FINANCE")
    public Result<Void> create(@Valid @RequestBody InvoiceCreateDTO dto,
                               @AuthenticationPrincipal SecurityUser user) {
        invoiceService.create(dto, user.getUserId());
        return Result.ok();
    }

    @PostMapping("/{id}/verify")
    @RequireRole("FINANCE")
    public Result<Void> verify(@PathVariable Long id) {
        invoiceService.verify(id);
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    @RequireRole("FINANCE")
    public Result<Void> reject(@PathVariable Long id, @RequestParam String reason) {
        invoiceService.reject(id, reason);
        return Result.ok();
    }
}
