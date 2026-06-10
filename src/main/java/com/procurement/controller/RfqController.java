package com.procurement.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.common.Result;
import com.procurement.entity.*;
import com.procurement.service.RfqService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/rfq")
@RequiredArgsConstructor
public class RfqController {

    private final RfqService rfqService;

    @Data
    public static class CreateRfqRequest {
        private String title;
        private LocalDateTime deadline;
        private List<RfqLine> lines;
        private List<Long> supplierIds;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Rfq> create(@RequestBody CreateRfqRequest req) {
        Rfq rfq = new Rfq();
        rfq.setTitle(req.getTitle());
        rfq.setDeadline(req.getDeadline());
        return Result.ok(rfqService.create(rfq, req.getLines(), req.getSupplierIds()));
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Rfq> publish(@PathVariable Long id) {
        return Result.ok(rfqService.publish(id));
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> close(@PathVariable Long id) {
        rfqService.close(id);
        return Result.ok();
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PURCHASER','PURCHASE_MANAGER')")
    public Result<Void> cancel(@PathVariable Long id) {
        rfqService.cancel(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<Rfq> getById(@PathVariable Long id) {
        return Result.ok(rfqService.getById(id));
    }

    @GetMapping("/{id}/lines")
    public Result<List<RfqLine>> getLines(@PathVariable Long id) {
        return Result.ok(rfqService.getLines(id));
    }

    @GetMapping
    public Result<Page<Rfq>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(rfqService.list(status, page, size));
    }
}
