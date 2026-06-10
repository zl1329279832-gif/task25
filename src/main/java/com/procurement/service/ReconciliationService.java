package com.procurement.service;

import com.procurement.entity.Reconciliation;
import com.procurement.entity.ReconciliationLine;
import java.util.List;

public interface ReconciliationService {
    /**
     * 生成对账单 - 自动匹配订单金额、收货金额、发票金额
     * 规则：
     * 1. 订单金额 = sum(po_line.quantity * po_line.unit_price)
     * 2. 收货金额 = sum(arrival_line.accepted_qty * po_line.unit_price)
     * 3. 发票金额 = sum(invoice_line.amount)
     * 4. 差异 = 发票金额 - 收货金额
     */
    Reconciliation generate(Long poId);

    void approve(Long reconId);
    void reject(Long reconId, String remark);
    Reconciliation getById(Long id);
    List<ReconciliationLine> getLines(Long reconId);
    List<Reconciliation> list(String status, Long supplierId, int page, int size);
}
