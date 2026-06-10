package com.procurement.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.entity.*;
import java.util.List;

public interface RfqService {
    Rfq create(Rfq rfq, List<RfqLine> lines, List<Long> supplierIds);
    Rfq publish(Long rfqId);
    void close(Long rfqId);
    void cancel(Long rfqId);
    Rfq getById(Long id);
    Page<Rfq> list(String status, int page, int size);
    List<RfqLine> getLines(Long rfqId);
}
