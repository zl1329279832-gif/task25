package com.procurement.service;

import com.procurement.entity.ReturnOrder;
import com.procurement.entity.ReturnLine;
import java.util.List;

public interface ReturnOrderService {
    ReturnOrder create(ReturnOrder returnOrder, List<ReturnLine> lines);
    void approve(Long id);
    void reject(Long id);
    void markReturned(Long id);
    ReturnOrder getById(Long id);
    List<ReturnLine> getLines(Long returnId);
}
