package com.procurement.controller;

import com.procurement.common.Result;
import com.procurement.entity.ReturnOrder;
import com.procurement.entity.ReturnLine;
import com.procurement.service.ReturnOrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnOrderController {

    private final ReturnOrderService returnService;

    @Data
    public static class CreateReturnRequest {
        private Long poId;
        private Long arrivalId;
        private Long supplierId;
        private String reason;
        private List<ReturnLine> lines;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PURCHASER','WAREHOUSE')")
    public Result<ReturnOrder> create(@RequestBody CreateReturnRequest req) {
        ReturnOrder ro = new ReturnOrder();
        ro.setPoId(req.getPoId());
        ro.setArrivalId(req.getArrivalId());
        ro.setSupplierId(req.getSupplierId());
        ro.setReason(req.getReason());
        return Result.ok(returnService.create(ro, req.getLines()));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> approve(@PathVariable Long id) {
        returnService.approve(id);
        return Result.ok();
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('PURCHASE_MANAGER')")
    public Result<Void> reject(@PathVariable Long id) {
        returnService.reject(id);
        return Result.ok();
    }

    @PutMapping("/{id}/mark-returned")
    @PreAuthorize("hasAnyRole('PURCHASER','WAREHOUSE')")
    public Result<Void> markReturned(@PathVariable Long id) {
        returnService.markReturned(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<ReturnOrder> getById(@PathVariable Long id) {
        return Result.ok(returnService.getById(id));
    }

    @GetMapping("/{id}/lines")
    public Result<List<ReturnLine>> getLines(@PathVariable Long id) {
        return Result.ok(returnService.getLines(id));
    }
}
