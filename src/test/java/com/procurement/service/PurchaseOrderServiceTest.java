package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.PoStatus;
import com.procurement.entity.PurchaseOrder;
import com.procurement.entity.PurchaseOrderLine;
import com.procurement.mapper.ApprovalMapper;
import com.procurement.mapper.PurchaseOrderLineMapper;
import com.procurement.mapper.PurchaseOrderMapper;
import com.procurement.security.LoginUser;
import com.procurement.service.impl.PurchaseOrderServiceImpl;
import com.procurement.state.PurchaseOrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @InjectMocks
    private PurchaseOrderServiceImpl poService;

    @Mock
    private PurchaseOrderMapper poMapper;

    @Mock
    private PurchaseOrderLineMapper poLineMapper;

    @Mock
    private ApprovalMapper approvalMapper;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(1L, "purchaser01", "PURCHASER", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    @Test
    void createPO_shouldSetDraftStatus() {
        PurchaseOrder po = new PurchaseOrder();
        po.setSupplierId(1L);
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setMaterialId(1L);
        line.setQuantity(new BigDecimal("100"));
        line.setUnitPrice(new BigDecimal("10.5"));
        line.setReceivedQty(BigDecimal.ZERO);

        when(poMapper.insert(any())).thenReturn(1);
        when(poLineMapper.insert(any())).thenReturn(1);

        PurchaseOrder result = poService.create(po, List.of(line));

        assertEquals(PoStatus.DRAFT.name(), result.getStatus());
        assertNotNull(result.getPoNo());
        assertEquals(new BigDecimal("1050.0"), result.getTotalAmount());
        verify(poMapper).insert(any());
        verify(poLineMapper).insert(any());
    }

    @Test
    void cancelOrder_shouldRejectWhenPartiallyReceived() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setStatus(PoStatus.PARTIAL_RECEIVED.name());
        when(poMapper.selectById(1L)).thenReturn(po);

        assertThrows(BusinessException.class, () -> poService.cancel(1L, "test"));
    }

    @Test
    void cancelOrder_shouldSucceedWhenConfirmed() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setStatus(PoStatus.CONFIRMED.name());
        when(poMapper.selectById(1L)).thenReturn(po);
        when(poMapper.updateById(any())).thenReturn(1);

        poService.cancel(1L, "需求变更");

        assertEquals(PoStatus.CANCELLED.name(), po.getStatus());
        assertEquals("需求变更", po.getCancelReason());
    }

    @Test
    void approve_shouldTransitionToApproved() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setStatus(PoStatus.PENDING_APPROVAL.name());
        when(poMapper.selectById(1L)).thenReturn(po);
        when(poMapper.updateById(any())).thenReturn(1);
        when(approvalMapper.selectOne(any())).thenReturn(null);

        poService.approve(1L, 2L);

        assertEquals(PoStatus.APPROVED.name(), po.getStatus());
        assertNotNull(po.getApprovedAt());
    }

    @Test
    void stateMachine_shouldPreventInvalidTransitions() {
        assertThrows(BusinessException.class, () ->
                PurchaseOrderStateMachine.validateTransition(PoStatus.RECEIVED, PoStatus.CANCELLED));

        assertThrows(BusinessException.class, () ->
                PurchaseOrderStateMachine.validateTransition(PoStatus.DRAFT, PoStatus.RECEIVED));

        // Valid transitions should not throw
        assertDoesNotThrow(() ->
                PurchaseOrderStateMachine.validateTransition(PoStatus.DRAFT, PoStatus.PENDING_APPROVAL));
    }

    @Test
    void canCancel_shouldReturnFalseForReceivedOrders() {
        assertFalse(PurchaseOrderStateMachine.canCancel(PoStatus.PARTIAL_RECEIVED));
        assertFalse(PurchaseOrderStateMachine.canCancel(PoStatus.RECEIVED));
        assertFalse(PurchaseOrderStateMachine.canCancel(PoStatus.CANCELLED));
        assertTrue(PurchaseOrderStateMachine.canCancel(PoStatus.CONFIRMED));
        assertTrue(PurchaseOrderStateMachine.canCancel(PoStatus.DRAFT));
    }
}
