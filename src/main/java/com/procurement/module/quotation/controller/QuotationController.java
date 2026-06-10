package com.procurement.module.quotation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.annotation.RequireRole;
import com.procurement.common.result.Result;
import com.procurement.module.quotation.dto.QuotationSubmitDTO;
import com.procurement.module.quotation.service.QuotationService;
import com.procurement.module.quotation.vo.QuotationVO;
import com.procurement.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quotations")
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationService quotationService;

    @PostMapping
    @RequireRole("SUPPLIER")
    public Result<Void> submit(@Valid @RequestBody QuotationSubmitDTO dto,
                               @AuthenticationPrincipal SecurityUser user) {
        quotationService.submit(dto, user.getSupplierId());
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<QuotationVO> getById(@PathVariable Long id) {
        return Result.ok(quotationService.getById(id));
    }

    @GetMapping("/inquiry/{inquiryId}")
    @RequireRole({"BUYER", "PURCHASE_MANAGER"})
    public Result<List<QuotationVO>> listByInquiry(@PathVariable Long inquiryId) {
        return Result.ok(quotationService.listByInquiry(inquiryId));
    }

    @GetMapping("/my")
    @RequireRole("SUPPLIER")
    public Result<Page<QuotationVO>> myQuotations(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(quotationService.myQuotations(pageNum, pageSize, user.getSupplierId()));
    }
}
