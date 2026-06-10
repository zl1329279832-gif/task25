package com.procurement.module.inquiry.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.inquiry.dto.InquiryCreateDTO;
import com.procurement.module.inquiry.service.InquiryService;
import com.procurement.module.inquiry.vo.InquiryVO;
import com.procurement.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @GetMapping
    @RequireRole({"BUYER", "PURCHASE_MANAGER"})
    public Result<Page<InquiryVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(inquiryService.page(pageNum, pageSize, status, null));
    }

    @GetMapping("/{id}")
    public Result<InquiryVO> getById(@PathVariable Long id) {
        return Result.ok(inquiryService.getById(id));
    }

    @PostMapping
    @RequireRole("BUYER")
    public Result<Void> create(@Valid @RequestBody InquiryCreateDTO dto,
                               @AuthenticationPrincipal SecurityUser user) {
        inquiryService.create(dto, user.getUserId());
        return Result.ok();
    }

    @PostMapping("/{id}/publish")
    @RequireRole("BUYER")
    public Result<Void> publish(@PathVariable Long id) {
        inquiryService.publish(id);
        return Result.ok();
    }

    @PostMapping("/{id}/close")
    @RequireRole({"BUYER", "PURCHASE_MANAGER"})
    public Result<Void> close(@PathVariable Long id) {
        inquiryService.close(id);
        return Result.ok();
    }

    @PostMapping("/{id}/cancel")
    @RequireRole("BUYER")
    public Result<Void> cancel(@PathVariable Long id) {
        inquiryService.cancel(id);
        return Result.ok();
    }

    @GetMapping("/supplier")
    @RequireRole("SUPPLIER")
    public Result<Page<InquiryVO>> supplierInquiries(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(inquiryService.pageForSupplier(pageNum, pageSize, user.getSupplierId()));
    }
}
