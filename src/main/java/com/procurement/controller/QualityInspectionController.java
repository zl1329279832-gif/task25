package com.procurement.controller;

import com.procurement.common.Result;
import com.procurement.entity.QualityInspection;
import com.procurement.service.QualityInspectionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inspections")
@RequiredArgsConstructor
public class QualityInspectionController {

    private final QualityInspectionService inspectionService;

    @Data
    public static class InspectRequest {
        private Long arrivalId;
        private String result; // PASS / FAIL / CONDITIONAL
        private String remark;
        private List<Map<String, Object>> lineResults;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE','PURCHASE_MANAGER')")
    public Result<QualityInspection> inspect(@RequestBody InspectRequest req) {
        return Result.ok(inspectionService.inspect(
                req.getArrivalId(), req.getResult(), req.getLineResults(), req.getRemark()));
    }

    @GetMapping("/arrival/{arrivalId}")
    public Result<QualityInspection> getByArrival(@PathVariable Long arrivalId) {
        return Result.ok(inspectionService.getByArrivalId(arrivalId));
    }
}
