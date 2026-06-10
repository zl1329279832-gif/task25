package com.procurement.module.supplier.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.supplier.dto.SupplierCreateDTO;
import com.procurement.module.supplier.dto.SupplierUpdateDTO;
import com.procurement.module.supplier.service.SupplierService;
import com.procurement.module.supplier.vo.SupplierVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @RequireRole({"BUYER", "PURCHASE_MANAGER"})
    public Result<Page<SupplierVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(supplierService.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    @RequireRole({"BUYER", "PURCHASE_MANAGER"})
    public Result<SupplierVO> getById(@PathVariable Long id) {
        return Result.ok(supplierService.getById(id));
    }

    @PostMapping
    @RequireRole("PURCHASE_MANAGER")
    public Result<Void> create(@Valid @RequestBody SupplierCreateDTO dto) {
        supplierService.create(dto);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @RequireRole("PURCHASE_MANAGER")
    public Result<Void> update(@PathVariable Long id, @RequestBody SupplierUpdateDTO dto) {
        supplierService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/qualification")
    @RequireRole("PURCHASE_MANAGER")
    public Result<Void> updateQualification(@PathVariable Long id, @RequestParam String status) {
        supplierService.updateQualification(id, status);
        return Result.ok();
    }

    @GetMapping("/qualified")
    @RequireRole({"BUYER", "PURCHASE_MANAGER"})
    public Result<List<SupplierVO>> listQualified() {
        return Result.ok(supplierService.listQualified());
    }
}
