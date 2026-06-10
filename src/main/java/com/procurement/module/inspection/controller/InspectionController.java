package com.procurement.module.inspection.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.inspection.dto.InspectionCreateDTO;
import com.procurement.module.inspection.service.InspectionService;
import com.procurement.module.inspection.vo.InspectionVO;
import com.procurement.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    @GetMapping
    @RequireRole({"WAREHOUSE", "BUYER"})
    public Result<Page<InspectionVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long deliveryId) {
        return Result.ok(inspectionService.page(pageNum, pageSize, deliveryId));
    }

    @GetMapping("/{id}")
    @RequireRole({"WAREHOUSE", "BUYER"})
    public Result<InspectionVO> getById(@PathVariable Long id) {
        return Result.ok(inspectionService.getById(id));
    }

    @PostMapping
    @RequireRole("WAREHOUSE")
    public Result<Void> create(@Valid @RequestBody InspectionCreateDTO dto,
                               @AuthenticationPrincipal SecurityUser user) {
        inspectionService.create(dto, user.getUserId());
        return Result.ok();
    }
}
