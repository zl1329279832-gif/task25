package com.procurement.service;

import com.procurement.entity.Invoice;
import com.procurement.entity.InvoiceLine;
import java.util.List;

public interface InvoiceService {
    Invoice register(Invoice invoice, List<InvoiceLine> lines);
    void verify(Long id);
    void reject(Long id);
    Invoice getById(Long id);
    List<InvoiceLine> getLines(Long invoiceId);
    List<Invoice> getByPoId(Long poId);
}
