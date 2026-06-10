package com.procurement.service;

import com.procurement.entity.Arrival;
import com.procurement.entity.ArrivalLine;
import java.util.List;

public interface ArrivalService {
    /**
     * 创建分批到货记录，自动计算差异
     */
    Arrival createArrival(Arrival arrival, List<ArrivalLine> lines);

    /**
     * 完成质检后更新到货单状态
     */
    void updateStatus(Long arrivalId, String status);

    Arrival getById(Long id);
    List<ArrivalLine> getLines(Long arrivalId);
    List<Arrival> getByPoId(Long poId);
}
