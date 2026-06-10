package com.procurement.module.material.controller;

import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.material.service.MaterialCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/material-categories")
@RequiredArgsConstructor
public class MaterialCategoryController {

    private final MaterialCategoryService categoryService;

    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree() {
        return Result.ok(categoryService.tree());
    }

    @PostMapping
    @RequireRole("PURCHASE_MANAGER")
    public Result<Void> create(@RequestParam String categoryCode,
                               @RequestParam String categoryName,
                               @RequestParam(required = false) Long parentId,
                               @RequestParam(required = false) Integer sortOrder) {
        categoryService.create(categoryCode, categoryName, parentId, sortOrder);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @RequireRole("PURCHASE_MANAGER")
    public Result<Void> update(@PathVariable Long id,
                               @RequestParam(required = false) String categoryName,
                               @RequestParam(required = false) Integer sortOrder) {
        categoryService.update(id, categoryName, sortOrder);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole("PURCHASE_MANAGER")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
