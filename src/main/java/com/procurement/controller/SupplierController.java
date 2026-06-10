package com.procurement.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.Result;
import com.procurement.entity.Supplier;
import com.procurement.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Supplier> create(@RequestBody Supplier supplier) {
        return Result.ok(supplierService.create(supplier));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Supplier> update(@PathVariable Long id, @RequestBody Supplier supplier) {
        return Result.ok(supplierService.update(id, supplier));
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> disable(@PathVariable Long id) {
        supplierService.disable(id);
        return Result.ok();
    }

    @PutMapping("/{id}/blacklist")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> blacklist(@PathVariable Long id) {
        supplierService.blacklist(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<Supplier> getById(@PathVariable Long id) {
        return Result.ok(supplierService.getById(id));
    }

    @GetMapping
    public Result<Page<Supplier>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(supplierService.list(keyword, page, size));
    }
}
