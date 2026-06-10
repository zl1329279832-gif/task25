package com.procurement.controller;

import com.procurement.common.BusinessException;
import com.procurement.common.Result;
import com.procurement.entity.Invoice;
import com.procurement.entity.InvoiceLine;
import com.procurement.security.LoginUser;
import com.procurement.service.InvoiceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @Data
    public static class RegisterInvoiceRequest {
        private String invoiceNo;
        private Long poId;
        private Long supplierId;
        private BigDecimal amount;
        private BigDecimal taxAmount;
        private LocalDate invoiceDate;
        private List<InvoiceLine> lines;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FINANCE','SUPPLIER')")
    public Result<Invoice> register(@RequestBody RegisterInvoiceRequest req) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo(req.getInvoiceNo());
        invoice.setPoId(req.getPoId());
        invoice.setSupplierId(req.getSupplierId());
        invoice.setAmount(req.getAmount());
        invoice.setTaxAmount(req.getTaxAmount());
        invoice.setInvoiceDate(req.getInvoiceDate());
        return Result.ok(invoiceService.register(invoice, req.getLines()));
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasRole('FINANCE')")
    public Result<Void> verify(@PathVariable Long id) {
        invoiceService.verify(id);
        return Result.ok();
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('FINANCE')")
    public Result<Void> reject(@PathVariable Long id) {
        invoiceService.reject(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<Invoice> getById(@PathVariable Long id) {
        Invoice invoice = invoiceService.getById(id);
        LoginUser user = getCurrentUser();
        if ("SUPPLIER".equals(user.getRole()) && !user.getSupplierId().equals(invoice.getSupplierId())) {
            throw new BusinessException("无权查看其他供应商的发票");
        }
        return Result.ok(invoice);
    }

    @GetMapping("/{id}/lines")
    public Result<List<InvoiceLine>> getLines(@PathVariable Long id) {
        return Result.ok(invoiceService.getLines(id));
    }

    @GetMapping("/po/{poId}")
    public Result<List<Invoice>> getByPo(@PathVariable Long poId) {
        return Result.ok(invoiceService.getByPoId(poId));
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
