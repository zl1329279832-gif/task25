package com.procurement.module.approval.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.approval.service.ApprovalService;
import com.procurement.module.approval.vo.ApprovalRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/pending")
    @RequireRole("PURCHASE_MANAGER")
    public Result<Page<ApprovalRecordVO>> pending(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(approvalService.pendingPage(pageNum, pageSize));
    }

    @GetMapping("/business/{type}/{id}")
    public Result<Page<ApprovalRecordVO>> businessHistory(
            @PathVariable String type,
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(approvalService.historyByBusiness(type, id, pageNum, pageSize));
    }
}
