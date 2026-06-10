package com.procurement.module.returns.service;

import com.procurement.common.enums.ReturnStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.returns.dto.ReturnCreateDTO;
import com.procurement.module.returns.entity.ReturnOrder;
import com.procurement.module.returns.mapper.ReturnItemMapper;
import com.procurement.module.returns.mapper.ReturnOrderMapper;
import com.procurement.module.returns.statemachine.ReturnStateMachine;
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
class ReturnOrderServiceTest {

    @Mock private ReturnOrderMapper returnOrderMapper;
    @Mock private ReturnItemMapper returnItemMapper;
    @Mock private ReturnStateMachine stateMachine;
    @Mock private CodeGenerator codeGenerator;

    @InjectMocks
    private ReturnOrderService service;

    @Test
    void create_shouldSetPendingStatus() {
        when(codeGenerator.generate("RET")).thenReturn("RET-20260101-0001");
        when(returnOrderMapper.insert(any())).thenReturn(1);
        when(returnItemMapper.insert(any())).thenReturn(1);
        when(returnOrderMapper.updateById(any())).thenReturn(1);

        ReturnCreateDTO.Item item = new ReturnCreateDTO.Item();
        item.setMaterialId(1L);
        item.setReturnQuantity(new BigDecimal("10"));
        item.setUnitPrice(new BigDecimal("100"));

        ReturnCreateDTO dto = new ReturnCreateDTO();
        dto.setOrderId(1L);
        dto.setSupplierId(1L);
        dto.setReason("Quality issue");
        dto.setItems(List.of(item));

        service.create(dto, 1L);

        ArgumentCaptor<ReturnOrder> captor = ArgumentCaptor.forClass(ReturnOrder.class);
        verify(returnOrderMapper).insert(captor.capture());
        assertEquals(ReturnStatus.PENDING.name(), captor.getValue().getStatus());
    }

    @Test
    void create_shouldCalculateTotalAmount() {
        when(codeGenerator.generate("RET")).thenReturn("RET-20260101-0002");
        when(returnOrderMapper.insert(any())).thenReturn(1);
        when(returnItemMapper.insert(any())).thenReturn(1);
        when(returnOrderMapper.updateById(any())).thenReturn(1);

        ReturnCreateDTO.Item item1 = new ReturnCreateDTO.Item();
        item1.setMaterialId(1L);
        item1.setReturnQuantity(new BigDecimal("10"));
        item1.setUnitPrice(new BigDecimal("100"));

        ReturnCreateDTO.Item item2 = new ReturnCreateDTO.Item();
        item2.setMaterialId(2L);
        item2.setReturnQuantity(new BigDecimal("5"));
        item2.setUnitPrice(new BigDecimal("200"));

        ReturnCreateDTO dto = new ReturnCreateDTO();
        dto.setOrderId(1L);
        dto.setSupplierId(1L);
        dto.setReason("Defective parts");
        dto.setItems(List.of(item1, item2));

        service.create(dto, 1L);

        ArgumentCaptor<ReturnOrder> updateCaptor = ArgumentCaptor.forClass(ReturnOrder.class);
        verify(returnOrderMapper).updateById(updateCaptor.capture());
        // 10*100 + 5*200 = 2000
        assertEquals(new BigDecimal("2000"), updateCaptor.getValue().getTotalAmount());
    }

    @Test
    void supplierConfirm_shouldTransitionState() {
        ReturnOrder ro = new ReturnOrder();
        ro.setId(1L);
        ro.setStatus(ReturnStatus.PENDING.name());
        when(returnOrderMapper.selectById(1L)).thenReturn(ro);
        when(returnOrderMapper.updateById(any())).thenReturn(1);

        service.supplierConfirm(1L);

        assertEquals(ReturnStatus.SUPPLIER_CONFIRMED.name(), ro.getStatus());
        assertNotNull(ro.getSupplierConfirmTime());
    }

    @Test
    void complete_shouldThrow_whenStatusIsPending() {
        ReturnOrder ro = new ReturnOrder();
        ro.setId(1L);
        ro.setStatus(ReturnStatus.PENDING.name());
        when(returnOrderMapper.selectById(1L)).thenReturn(ro);
        doThrow(new BizException(com.procurement.common.exception.ErrorCode.RETURN_STATUS_ERROR))
                .when(stateMachine).validateTransition(ReturnStatus.PENDING, ReturnStatus.COMPLETED);

        assertThrows(BizException.class, () -> service.complete(1L));
    }
}
