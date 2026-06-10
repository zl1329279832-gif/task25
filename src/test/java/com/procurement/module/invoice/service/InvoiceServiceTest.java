package com.procurement.module.invoice.service;

import com.procurement.common.enums.InvoiceStatus;
import com.procurement.common.exception.BizException;
import com.procurement.module.invoice.dto.InvoiceCreateDTO;
import com.procurement.module.invoice.entity.Invoice;
import com.procurement.module.invoice.mapper.InvoiceItemMapper;
import com.procurement.module.invoice.mapper.InvoiceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceMapper invoiceMapper;
    @Mock private InvoiceItemMapper invoiceItemMapper;

    @InjectMocks
    private InvoiceService service;

    @Test
    void create_shouldSetRegisteredStatus() {
        when(invoiceMapper.insert(any())).thenReturn(1);

        InvoiceCreateDTO dto = new InvoiceCreateDTO();
        dto.setInvoiceNo("INV-001");
        dto.setOrderId(1L);
        dto.setSupplierId(1L);
        dto.setInvoiceType("NORMAL");
        dto.setAmount(new BigDecimal("10000"));
        dto.setTaxAmount(new BigDecimal("1300"));
        dto.setTotalAmount(new BigDecimal("11300"));
        dto.setInvoiceDate(LocalDate.now());
        dto.setItems(Collections.emptyList());

        service.create(dto, 1L);

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceMapper).insert(captor.capture());
        assertEquals(InvoiceStatus.REGISTERED.name(), captor.getValue().getStatus());
    }

    @Test
    void verify_shouldChangeStatusToVerified() {
        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setStatus(InvoiceStatus.REGISTERED.name());
        when(invoiceMapper.selectById(1L)).thenReturn(invoice);
        when(invoiceMapper.updateById(any())).thenReturn(1);

        service.verify(1L);

        assertEquals(InvoiceStatus.VERIFIED.name(), invoice.getStatus());
        assertNotNull(invoice.getVerifyTime());
    }

    @Test
    void verify_shouldThrow_whenAlreadyVerified() {
        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setStatus(InvoiceStatus.VERIFIED.name());
        when(invoiceMapper.selectById(1L)).thenReturn(invoice);

        assertThrows(BizException.class, () -> service.verify(1L));
    }

    @Test
    void reject_shouldSetRejectReason() {
        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setStatus(InvoiceStatus.REGISTERED.name());
        when(invoiceMapper.selectById(1L)).thenReturn(invoice);
        when(invoiceMapper.updateById(any())).thenReturn(1);

        service.reject(1L, "金额不符");

        assertEquals(InvoiceStatus.REJECTED.name(), invoice.getStatus());
        assertEquals("金额不符", invoice.getRejectReason());
    }

    @Test
    void reject_shouldThrow_whenAlreadyRejected() {
        Invoice invoice = new Invoice();
        invoice.setId(1L);
        invoice.setStatus(InvoiceStatus.REJECTED.name());
        when(invoiceMapper.selectById(1L)).thenReturn(invoice);

        assertThrows(BizException.class, () -> service.reject(1L, "test"));
    }
}
