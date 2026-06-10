package com.procurement.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.entity.PurchaseOrder;
import com.procurement.entity.PurchaseOrderLine;
import java.util.List;

public interface PurchaseOrderService {
    PurchaseOrder create(PurchaseOrder po, List<PurchaseOrderLine> lines);
    void submitForApproval(Long poId);
    void approve(Long poId, Long approverId);
    void reject(Long poId, Long approverId, String comment);
    void confirm(Long poId);
    /**
     * 取消订单 - 已收货部分不能取消
     */
    void cancel(Long poId, String reason);
    PurchaseOrder getById(Long id);
    List<PurchaseOrderLine> getLines(Long poId);
    Page<PurchaseOrder> list(String status, Long supplierId, int page, int size);
}
