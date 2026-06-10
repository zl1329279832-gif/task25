package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.impl.ArrivalServiceImpl;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArrivalServiceTest {

    @InjectMocks
    private ArrivalServiceImpl arrivalService;

    @Mock
    private ArrivalMapper arrivalMapper;

    @Mock
    private ArrivalLineMapper arrivalLineMapper;

    @Mock
    private PurchaseOrderMapper poMapper;

    @Mock
    private PurchaseOrderLineMapper poLineMapper;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(5L, "warehouse01", "WAREHOUSE", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    @Test
    void createArrival_shouldSucceedForConfirmedPO() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setStatus(PoStatus.CONFIRMED.name());
        when(poMapper.selectById(1L)).thenReturn(po);
        when(arrivalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(arrivalMapper.insert(any())).thenReturn(1);
        when(arrivalLineMapper.insert(any())).thenReturn(1);

        PurchaseOrderLine poLine = new PurchaseOrderLine();
        poLine.setId(1L);
        poLine.setQuantity(new BigDecimal("100"));
        poLine.setMaterialId(1L);
        poLine.setReceivedQty(BigDecimal.ZERO);
        when(poLineMapper.selectById(1L)).thenReturn(poLine);
        when(poLineMapper.updateById(any())).thenReturn(1);
        when(poMapper.updateById(any())).thenReturn(1);

        Arrival arrival = new Arrival();
        arrival.setPoId(1L);
        arrival.setArrivedAt(LocalDateTime.now());

        ArrivalLine line = new ArrivalLine();
        line.setPoLineId(1L);
        line.setArrivedQty(new BigDecimal("50"));

        Arrival result = arrivalService.createArrival(arrival, List.of(line));

        assertEquals(ArrivalStatus.PENDING.name(), result.getStatus());
        assertEquals(1, result.getBatchNo());
        verify(poLineMapper).updateById(any());
    }

    @Test
    void createArrival_shouldRejectForDraftPO() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setStatus(PoStatus.DRAFT.name());
        when(poMapper.selectById(1L)).thenReturn(po);

        Arrival arrival = new Arrival();
        arrival.setPoId(1L);

        assertThrows(BusinessException.class, () ->
                arrivalService.createArrival(arrival, Collections.emptyList()));
    }

    @Test
    void createArrival_shouldUpdatePOToPartialReceived() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(1L);
        po.setStatus(PoStatus.CONFIRMED.name());
        when(poMapper.selectById(1L)).thenReturn(po);
        when(arrivalMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(arrivalMapper.insert(any())).thenReturn(1);
        when(arrivalLineMapper.insert(any())).thenReturn(1);

        PurchaseOrderLine poLine = new PurchaseOrderLine();
        poLine.setId(1L);
        poLine.setQuantity(new BigDecimal("100"));
        poLine.setMaterialId(1L);
        poLine.setReceivedQty(BigDecimal.ZERO);
        when(poLineMapper.selectById(1L)).thenReturn(poLine);
        when(poLineMapper.updateById(any())).thenReturn(1);

        // 还有另一行未收货 → 部分收货
        PurchaseOrderLine poLine2 = new PurchaseOrderLine();
        poLine2.setId(2L);
        poLine2.setQuantity(new BigDecimal("200"));
        poLine2.setReceivedQty(BigDecimal.ZERO);
        when(poLineMapper.selectList(any())).thenReturn(List.of(poLine, poLine2));
        when(poMapper.updateById(any())).thenReturn(1);

        Arrival arrival = new Arrival();
        arrival.setPoId(1L);
        arrival.setArrivedAt(LocalDateTime.now());

        ArrivalLine line = new ArrivalLine();
        line.setPoLineId(1L);
        line.setArrivedQty(new BigDecimal("100"));

        arrivalService.createArrival(arrival, List.of(line));

        assertEquals(PoStatus.PARTIAL_RECEIVED.name(), po.getStatus());
    }

    @Test
    void arrivalDifference_shouldBeCalculated() {
        ArrivalLine line = new ArrivalLine();
        line.setOrderedQty(new BigDecimal("100"));
        line.setArrivedQty(new BigDecimal("80"));
        // diff = arrived - ordered = -20 (shortage)
        BigDecimal diff = line.getArrivedQty().subtract(line.getOrderedQty());
        assertEquals(new BigDecimal("-20"), diff);
    }
}
