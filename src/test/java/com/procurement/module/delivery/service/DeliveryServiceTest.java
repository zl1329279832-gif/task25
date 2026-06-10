package com.procurement.module.delivery.service;

import com.procurement.common.util.CodeGenerator;
import com.procurement.module.delivery.dto.DeliveryCreateDTO;
import com.procurement.module.delivery.entity.DeliveryDiff;
import com.procurement.module.delivery.mapper.DeliveryDiffMapper;
import com.procurement.module.delivery.mapper.DeliveryItemMapper;
import com.procurement.module.delivery.mapper.DeliveryMapper;
import com.procurement.module.order.entity.OrderItem;
import com.procurement.module.order.entity.PurchaseOrder;
import com.procurement.module.order.mapper.OrderItemMapper;
import com.procurement.module.order.mapper.PurchaseOrderMapper;
import com.procurement.module.order.service.PurchaseOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock private DeliveryMapper deliveryMapper;
    @Mock private DeliveryItemMapper deliveryItemMapper;
    @Mock private DeliveryDiffMapper diffMapper;
    @Mock private PurchaseOrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private PurchaseOrderService orderService;
    @Mock private CodeGenerator codeGenerator;

    @InjectMocks
    private DeliveryService service;

    @Test
    void create_shouldGenerateDiffRecord_whenQuantityMismatch() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setSupplierId(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.generate("DLV")).thenReturn("DLV-20260101-0001");
        when(deliveryMapper.insert(any())).thenReturn(1);
        when(deliveryItemMapper.insert(any())).thenReturn(1);

        OrderItem oi = new OrderItem();
        oi.setReceivedQuantity(BigDecimal.ZERO);
        when(orderItemMapper.selectById(anyLong())).thenReturn(oi);
        when(orderItemMapper.updateById(any())).thenReturn(1);

        DeliveryCreateDTO.Item item = new DeliveryCreateDTO.Item();
        item.setOrderItemId(1L);
        item.setMaterialId(1L);
        item.setExpectedQuantity(new BigDecimal("100"));
        item.setActualQuantity(new BigDecimal("90")); // shortage of 10

        DeliveryCreateDTO dto = new DeliveryCreateDTO();
        dto.setOrderId(1L);
        dto.setDeliveryDate(LocalDate.now());
        dto.setItems(List.of(item));

        service.create(dto, 1L);

        ArgumentCaptor<DeliveryDiff> captor = ArgumentCaptor.forClass(DeliveryDiff.class);
        verify(diffMapper).insert(captor.capture());
        assertEquals("SHORTAGE", captor.getValue().getDiffType());
        assertEquals(new BigDecimal("10"), captor.getValue().getDiffQuantity());
    }

    @Test
    void create_shouldNotGenerateDiff_whenQuantitiesMatch() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setSupplierId(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.generate("DLV")).thenReturn("DLV-20260101-0002");
        when(deliveryMapper.insert(any())).thenReturn(1);
        when(deliveryItemMapper.insert(any())).thenReturn(1);

        OrderItem oi = new OrderItem();
        oi.setReceivedQuantity(BigDecimal.ZERO);
        when(orderItemMapper.selectById(anyLong())).thenReturn(oi);
        when(orderItemMapper.updateById(any())).thenReturn(1);

        DeliveryCreateDTO.Item item = new DeliveryCreateDTO.Item();
        item.setOrderItemId(1L);
        item.setMaterialId(1L);
        item.setExpectedQuantity(new BigDecimal("100"));
        item.setActualQuantity(new BigDecimal("100")); // exact match

        DeliveryCreateDTO dto = new DeliveryCreateDTO();
        dto.setOrderId(1L);
        dto.setDeliveryDate(LocalDate.now());
        dto.setItems(List.of(item));

        service.create(dto, 1L);

        verify(diffMapper, never()).insert(any());
    }

    @Test
    void create_shouldGenerateExcessDiff_whenActualExceedsExpected() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setSupplierId(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(codeGenerator.generate("DLV")).thenReturn("DLV-20260101-0003");
        when(deliveryMapper.insert(any())).thenReturn(1);
        when(deliveryItemMapper.insert(any())).thenReturn(1);

        OrderItem oi = new OrderItem();
        oi.setReceivedQuantity(BigDecimal.ZERO);
        when(orderItemMapper.selectById(anyLong())).thenReturn(oi);
        when(orderItemMapper.updateById(any())).thenReturn(1);

        DeliveryCreateDTO.Item item = new DeliveryCreateDTO.Item();
        item.setOrderItemId(1L);
        item.setMaterialId(1L);
        item.setExpectedQuantity(new BigDecimal("100"));
        item.setActualQuantity(new BigDecimal("120")); // excess of 20

        DeliveryCreateDTO dto = new DeliveryCreateDTO();
        dto.setOrderId(1L);
        dto.setDeliveryDate(LocalDate.now());
        dto.setItems(List.of(item));

        service.create(dto, 1L);

        ArgumentCaptor<DeliveryDiff> captor = ArgumentCaptor.forClass(DeliveryDiff.class);
        verify(diffMapper).insert(captor.capture());
        assertEquals("EXCESS", captor.getValue().getDiffType());
        assertEquals(new BigDecimal("20"), captor.getValue().getDiffQuantity());
    }
}
