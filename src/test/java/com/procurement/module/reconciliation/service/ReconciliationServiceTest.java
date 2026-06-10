package com.procurement.module.reconciliation.service;

import com.procurement.common.enums.ReconciliationStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.delivery.mapper.DeliveryItemMapper;
import com.procurement.module.delivery.mapper.DeliveryMapper;
import com.procurement.module.invoice.mapper.InvoiceMapper;
import com.procurement.module.order.mapper.OrderItemMapper;
import com.procurement.module.order.mapper.PurchaseOrderMapper;
import com.procurement.module.reconciliation.entity.Reconciliation;
import com.procurement.module.reconciliation.mapper.ReconciliationItemMapper;
import com.procurement.module.reconciliation.mapper.ReconciliationMapper;
import com.procurement.module.returns.service.ReturnOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock private ReconciliationMapper reconciliationMapper;
    @Mock private ReconciliationItemMapper reconciliationItemMapper;
    @Mock private PurchaseOrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private DeliveryMapper deliveryMapper;
    @Mock private DeliveryItemMapper deliveryItemMapper;
    @Mock private InvoiceMapper invoiceMapper;
    @Mock private ReturnOrderService returnOrderService;
    @Mock private CodeGenerator codeGenerator;

    @InjectMocks
    private ReconciliationService service;

    @Test
    void confirm_shouldChangeStatusToConfirmed() {
        Reconciliation recon = new Reconciliation();
        recon.setId(1L);
        recon.setStatus(ReconciliationStatus.DRAFT.name());
        when(reconciliationMapper.selectById(1L)).thenReturn(recon);
        when(reconciliationMapper.updateById(any())).thenReturn(1);

        service.confirm(1L);

        assertEquals(ReconciliationStatus.CONFIRMED.name(), recon.getStatus());
        assertNotNull(recon.getConfirmTime());
    }

    @Test
    void confirm_shouldThrow_whenNotDraft() {
        Reconciliation recon = new Reconciliation();
        recon.setId(1L);
        recon.setStatus(ReconciliationStatus.CONFIRMED.name());
        when(reconciliationMapper.selectById(1L)).thenReturn(recon);

        assertThrows(BizException.class, () -> service.confirm(1L));
    }

    @Test
    void dispute_shouldChangeStatusToDisputed() {
        Reconciliation recon = new Reconciliation();
        recon.setId(1L);
        recon.setStatus(ReconciliationStatus.DRAFT.name());
        when(reconciliationMapper.selectById(1L)).thenReturn(recon);
        when(reconciliationMapper.updateById(any())).thenReturn(1);

        service.dispute(1L, "发票金额不一致");

        assertEquals(ReconciliationStatus.DISPUTED.name(), recon.getStatus());
    }

    @Test
    void settle_shouldChangeStatusToSettled() {
        Reconciliation recon = new Reconciliation();
        recon.setId(1L);
        recon.setStatus(ReconciliationStatus.CONFIRMED.name());
        when(reconciliationMapper.selectById(1L)).thenReturn(recon);
        when(reconciliationMapper.updateById(any())).thenReturn(1);

        service.settle(1L);

        assertEquals(ReconciliationStatus.SETTLED.name(), recon.getStatus());
    }

    @Test
    void settle_shouldThrow_whenNotConfirmed() {
        Reconciliation recon = new Reconciliation();
        recon.setId(1L);
        recon.setStatus(ReconciliationStatus.DRAFT.name());
        when(reconciliationMapper.selectById(1L)).thenReturn(recon);

        assertThrows(BizException.class, () -> service.settle(1L));
    }
}
