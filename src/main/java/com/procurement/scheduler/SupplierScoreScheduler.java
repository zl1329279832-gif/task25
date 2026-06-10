package com.procurement.scheduler;

import com.procurement.service.SupplierScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierScoreScheduler {

    private final SupplierScoreService supplierScoreService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyRecalculate() {
        log.info("开始执行供应商评分定时重算...");
        try {
            supplierScoreService.recalculateAll();
            log.info("供应商评分定时重算完成");
        } catch (Exception e) {
            log.error("供应商评分定时重算异常: {}", e.getMessage(), e);
        }
    }
}
