package com.procurement.controller;

import com.procurement.common.Result;
import com.procurement.entity.Arrival;
import com.procurement.entity.ArrivalLine;
import com.procurement.service.ArrivalService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/arrivals")
@RequiredArgsConstructor
public class ArrivalController {

    private final ArrivalService arrivalService;

    @Data
    public static class CreateArrivalRequest {
        private Long poId;
        private LocalDateTime arrivedAt;
        private String remark;
        private List<ArrivalLine> lines;
    }

    @PostMapping
    @PreAuthorize("hasRole('WAREHOUSE')")
    public Result<Arrival> create(@RequestBody CreateArrivalRequest req) {
        Arrival arrival = new Arrival();
        arrival.setPoId(req.getPoId());
        arrival.setArrivedAt(req.getArrivedAt());
        arrival.setRemark(req.getRemark());
        return Result.ok(arrivalService.createArrival(arrival, req.getLines()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('WAREHOUSE','PURCHASE_MANAGER')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        arrivalService.updateStatus(id, status);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<Arrival> getById(@PathVariable Long id) {
        return Result.ok(arrivalService.getById(id));
    }

    @GetMapping("/{id}/lines")
    public Result<List<ArrivalLine>> getLines(@PathVariable Long id) {
        return Result.ok(arrivalService.getLines(id));
    }

    @GetMapping("/po/{poId}")
    public Result<List<Arrival>> getByPo(@PathVariable Long poId) {
        return Result.ok(arrivalService.getByPoId(poId));
    }
}
