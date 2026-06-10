package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.impl.ReconciliationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @InjectMocks
    private ReconciliationServiceImpl reconService;

    @Mock private ReconciliationMapper reconMapper;
    @Mock private ReconciliationLineMapper reconLineMapper;
    @Mock private PurchaseOrderMapper poMapper;
    @Mock private PurchaseOrderLineMapper poLineMapper;
    @Mock private ArrivalLineMapper arrivalLineMapper;
    @Mock private ArrivalMapper arrivalMapper;
    @Mock private InvoiceMapper invoiceMapper;
    @Mock private InvoiceLineMapper invoiceLineMapper;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(6L, "finance01", "FINANCE", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    @Test
    void generate_shouldMatchWhenAmountsEqual() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);
        when(poMapper.selectById(1L)).thenReturn(po);

        PurchaseOrderLine poLine = new PurchaseOrderLine();
        poLine.setId(1L);
        poLine.setMaterialId(1L);
        poLine.setQuantity(new BigDecimal("100"));
        poLine.setUnitPrice(new BigDecimal("10.00"));
        when(poLineMapper.selectList(any())).thenReturn(List.of(poLine));

        // 收货数量 = 100
        Arrival arrival = new Arrival();
        arrival.setId(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));

        ArrivalLine arrivalLine = new ArrivalLine();
        arrivalLine.setPoLineId(1L);
        arrivalLine.setAcceptedQty(new BigDecimal("100"));
        when(arrivalLineMapper.selectList(any())).thenReturn(List.of(arrivalLine));

        // 发票金额 = 1000（100 * 10）
        Invoice invoice = new Invoice();
        invoice.setAmount(new BigDecimal("1000.00"));
        when(invoiceMapper.selectList(any())).thenReturn(List.of(invoice));

        InvoiceLine invoiceLine = new InvoiceLine();
        invoiceLine.setPoLineId(1L);
        invoiceLine.setQuantity(new BigDecimal("100"));
        when(invoiceLineMapper.selectList(any())).thenReturn(List.of(invoiceLine));

        when(reconMapper.insert(any())).thenReturn(1);
        when(reconMapper.updateById(any())).thenReturn(1);
        when(reconLineMapper.insert(any())).thenReturn(1);

        Reconciliation result = reconService.generate(1L);

        assertEquals("MATCHED", result.getStatus());
        assertEquals(new BigDecimal("1000.00"), result.getOrderAmount());
        assertEquals(new BigDecimal("1000.00"), result.getReceiptAmount());
        assertEquals(new BigDecimal("1000.00"), result.getInvoiceAmount());
    }

    @Test
    void generate_shouldDetectDifference() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setSupplierId(1L);
        when(poMapper.selectById(1L)).thenReturn(po);

        PurchaseOrderLine poLine = new PurchaseOrderLine();
        poLine.setId(1L);
        poLine.setMaterialId(1L);
        poLine.setQuantity(new BigDecimal("100"));
        poLine.setUnitPrice(new BigDecimal("10.00"));
        when(poLineMapper.selectList(any())).thenReturn(List.of(poLine));

        // 只收了 80 个
        Arrival arrival = new Arrival();
        arrival.setId(1L);
        when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival));

        ArrivalLine arrivalLine = new ArrivalLine();
        arrivalLine.setPoLineId(1L);
        arrivalLine.setAcceptedQty(new BigDecimal("80"));
        when(arrivalLineMapper.selectList(any())).thenReturn(List.of(arrivalLine));

        // 发票开了 100 个的金额
        Invoice invoice = new Invoice();
        invoice.setAmount(new BigDecimal("1000.00"));
        when(invoiceMapper.selectList(any())).thenReturn(List.of(invoice));

        InvoiceLine invoiceLine = new InvoiceLine();
        invoiceLine.setPoLineId(1L);
        invoiceLine.setQuantity(new BigDecimal("100"));
        when(invoiceLineMapper.selectList(any())).thenReturn(List.of(invoiceLine));

        when(reconMapper.insert(any())).thenReturn(1);
        when(reconMapper.updateById(any())).thenReturn(1);
        when(reconLineMapper.insert(any())).thenReturn(1);

        Reconciliation result = reconService.generate(1L);

        assertEquals("DIFFERENT", result.getStatus());
        // 收货金额 = 80 * 10 = 800
        assertEquals(new BigDecimal("800.00"), result.getReceiptAmount());
        // 发票金额 = 1000
        assertEquals(new BigDecimal("1000.00"), result.getInvoiceAmount());
    }

    @Test
    void approve_shouldSucceed() {
        Reconciliation recon = new Reconciliation();
        recon.setId(1L);
        recon.setStatus("MATCHED");
        when(reconMapper.selectById(1L)).thenReturn(recon);
        when(reconMapper.updateById(any())).thenReturn(1);

        reconService.approve(1L);

        assertEquals("APPROVED", recon.getStatus());
    }
}
