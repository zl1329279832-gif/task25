package com.procurement.module.material.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.material.dto.MaterialCreateDTO;
import com.procurement.module.material.service.MaterialService;
import com.procurement.module.material.vo.MaterialVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    public Result<Page<MaterialVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId) {
        return Result.ok(materialService.page(pageNum, pageSize, keyword, categoryId));
    }

    @GetMapping("/{id}")
    public Result<MaterialVO> getById(@PathVariable Long id) {
        return Result.ok(materialService.getById(id));
    }

    @PostMapping
    @RequireRole("PURCHASE_MANAGER")
    public Result<Void> create(@Valid @RequestBody MaterialCreateDTO dto) {
        materialService.create(dto);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @RequireRole("PURCHASE_MANAGER")
    public Result<Void> update(@PathVariable Long id, @RequestBody MaterialCreateDTO dto) {
        materialService.update(id, dto);
        return Result.ok();
    }
}
