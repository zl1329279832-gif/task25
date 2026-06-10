package com.procurement.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.module.supplier.entity.Supplier;
import com.procurement.module.supplier.mapper.SupplierMapper;
import com.procurement.module.reconciliation.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Auto-generates reconciliation statements for the previous month.
 * Runs on the 1st of every month at 2:00 AM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationGenerateJob {

    private final SupplierMapper supplierMapper;
    private final ReconciliationService reconciliationService;

    @Scheduled(cron = "0 0 2 1 * ?")
    public void generateMonthlyReconciliation() {
        log.info("ReconciliationGenerateJob: generating reconciliations for last month...");

        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate periodEnd = today.withDayOfMonth(1).minusDays(1);

        // Get all qualified suppliers
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Supplier::getQualificationStatus, "QUALIFIED");
        List<Supplier> suppliers = supplierMapper.selectList(wrapper);

        int success = 0;
        int failed = 0;
        for (Supplier supplier : suppliers) {
            try {
                reconciliationService.generate(supplier.getId(), periodStart, periodEnd, null);
                success++;
                log.info("ReconciliationGenerateJob: generated reconciliation for supplier {} ({})",
                        supplier.getSupplierCode(), supplier.getSupplierName());
            } catch (Exception e) {
                failed++;
                log.error("ReconciliationGenerateJob: failed for supplier {} ({}): {}",
                        supplier.getSupplierCode(), supplier.getSupplierName(), e.getMessage());
            }
        }

        log.info("ReconciliationGenerateJob: completed. Success={}, Failed={}, Total={}",
                success, failed, suppliers.size());
    }
}
