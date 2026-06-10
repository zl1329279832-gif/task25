package com.procurement.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.common.enums.OrderStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.comparison.entity.ComparisonItem;
import com.procurement.module.comparison.mapper.ComparisonItemMapper;
import com.procurement.module.order.dto.OrderCreateDTO;
import com.procurement.module.order.entity.OrderItem;
import com.procurement.module.order.entity.PurchaseOrder;
import com.procurement.module.order.mapper.OrderItemMapper;
import com.procurement.module.order.mapper.PurchaseOrderMapper;
import com.procurement.module.quotation.entity.QuotationItem;
import com.procurement.module.quotation.mapper.QuotationItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock private PurchaseOrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private ComparisonItemMapper comparisonItemMapper;
    @Mock private QuotationItemMapper quotationItemMapper;
    @Mock private CodeGenerator codeGenerator;

    @InjectMocks
    private PurchaseOrderService service;

    @Test
    void create_autoApproval_whenAmountUnder10000() {
        ComparisonItem ci = new ComparisonItem();
        ci.setSupplierId(1L);
        ci.setQuotationId(10L);
        ci.setMaterialId(1L);
        ci.setUnitPrice(new BigDecimal("100"));
        ci.setIsSelected(1);

        QuotationItem qi = new QuotationItem();
        qi.setQuantity(new BigDecimal("50"));

        when(comparisonItemMapper.selectList(any())).thenReturn(List.of(ci));
        when(quotationItemMapper.selectList(any())).thenReturn(List.of(qi));
        when(codeGenerator.generate("PO")).thenReturn("PO-20260101-0001");
        when(orderMapper.insert(any())).thenReturn(1);
        when(orderItemMapper.insert(any())).thenReturn(1);
        when(orderMapper.updateById(any())).thenReturn(1);

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setComparisonId(1L);
        service.create(dto, 1L);

        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orderMapper).updateById(captor.capture());
        // total = 100 * 50 = 5000 < 10000, so auto-approved
        assertEquals(OrderStatus.APPROVED.name(), captor.getValue().getStatus());
        assertEquals("AUTO", captor.getValue().getApprovalThreshold());
    }

    @Test
    void create_level1Approval_whenAmountBetween10000And100000() {
        ComparisonItem ci = new ComparisonItem();
        ci.setSupplierId(1L);
        ci.setQuotationId(10L);
        ci.setMaterialId(1L);
        ci.setUnitPrice(new BigDecimal("500"));
        ci.setIsSelected(1);

        QuotationItem qi = new QuotationItem();
        qi.setQuantity(new BigDecimal("100"));

        when(comparisonItemMapper.selectList(any())).thenReturn(List.of(ci));
        when(quotationItemMapper.selectList(any())).thenReturn(List.of(qi));
        when(codeGenerator.generate("PO")).thenReturn("PO-20260101-0002");
        when(orderMapper.insert(any())).thenReturn(1);
        when(orderItemMapper.insert(any())).thenReturn(1);
        when(orderMapper.updateById(any())).thenReturn(1);

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setComparisonId(1L);
        service.create(dto, 1L);

        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orderMapper).updateById(captor.capture());
        // total = 500 * 100 = 50000, needs LEVEL_1
        assertEquals(OrderStatus.PENDING_APPROVAL.name(), captor.getValue().getStatus());
        assertEquals("LEVEL_1", captor.getValue().getApprovalThreshold());
    }

    @Test
    void cancel_shouldThrow_whenItemsHaveBeenReceived() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.CONFIRMED.name());
        when(orderMapper.selectById(1L)).thenReturn(order);

        OrderItem item = new OrderItem();
        item.setReceivedQuantity(new BigDecimal("10"));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));

        assertThrows(BizException.class, () -> service.cancel(1L));
    }

    @Test
    void cancel_shouldSucceed_whenNoItemsReceived() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.CONFIRMED.name());
        when(orderMapper.selectById(1L)).thenReturn(order);

        OrderItem item = new OrderItem();
        item.setReceivedQuantity(BigDecimal.ZERO);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(orderMapper.updateById(any())).thenReturn(1);

        assertDoesNotThrow(() -> service.cancel(1L));
    }
}
