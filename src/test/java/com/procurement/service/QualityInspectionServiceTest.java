package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.impl.QualityInspectionServiceImpl;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QualityInspectionServiceTest {

    @InjectMocks
    private QualityInspectionServiceImpl inspectionService;

    @Mock private QualityInspectionMapper inspectionMapper;
    @Mock private ArrivalMapper arrivalMapper;
    @Mock private ArrivalLineMapper arrivalLineMapper;
    @Mock private PurchaseOrderLineMapper poLineMapper;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(4L, "warehouse01", "WAREHOUSE", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    @Test
    void inspect_shouldUpdatePoLineAcceptedAndRejectedQty() {
        // 到货单处于 PENDING 状态
        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setStatus(ArrivalStatus.PENDING.name());
        when(arrivalMapper.selectById(1L)).thenReturn(arrival);

        // 到货行：到货 100 个
        ArrivalLine arrivalLine = new ArrivalLine();
        arrivalLine.setId(10L);
        arrivalLine.setPoLineId(100L);
        arrivalLine.setArrivedQty(new BigDecimal("100"));
        arrivalLine.setAcceptedQty(BigDecimal.ZERO);
        when(arrivalLineMapper.selectById(10L)).thenReturn(arrivalLine);

        // PO 行初始状态
        PurchaseOrderLine poLine = new PurchaseOrderLine();
        poLine.setId(100L);
        poLine.setAcceptedQty(BigDecimal.ZERO);
        poLine.setRejectedQty(BigDecimal.ZERO);
        when(poLineMapper.selectById(100L)).thenReturn(poLine);

        when(inspectionMapper.insert(any())).thenReturn(1);
        when(arrivalLineMapper.updateById(any())).thenReturn(1);
        when(poLineMapper.updateById(any())).thenReturn(1);
        when(arrivalMapper.updateById(any())).thenReturn(1);

        // 质检验收 70 个，退回 30 个
        List<Map<String, Object>> lineResults = List.of(
                Map.of("lineId", "10", "acceptedQty", "70")
        );

        QualityInspection result = inspectionService.inspect(1L, "CONDITIONAL", lineResults, "部分不合格");

        assertNotNull(result);
        // 验证 PO 行的 acceptedQty 和 rejectedQty 被正确更新
        assertEquals(new BigDecimal("70"), poLine.getAcceptedQty());
        assertEquals(new BigDecimal("30"), poLine.getRejectedQty());
        // 验证到货单状态转换为 PARTIAL_ACCEPTED
        assertEquals(ArrivalStatus.PARTIAL_ACCEPTED.name(), arrival.getStatus());
    }

    @Test
    void inspect_shouldRejectInvalidStatusTransition() {
        // 到货单已经处于 ACCEPTED 终态
        Arrival arrival = new Arrival();
        arrival.setId(1L);
        arrival.setStatus(ArrivalStatus.ACCEPTED.name());
        when(arrivalMapper.selectById(1L)).thenReturn(arrival);
        when(inspectionMapper.insert(any())).thenReturn(1);

        ArrivalLine arrivalLine = new ArrivalLine();
        arrivalLine.setId(10L);
        arrivalLine.setPoLineId(100L);
        arrivalLine.setArrivedQty(new BigDecimal("100"));
        when(arrivalLineMapper.selectById(10L)).thenReturn(arrivalLine);

        PurchaseOrderLine poLine = new PurchaseOrderLine();
        poLine.setId(100L);
        poLine.setAcceptedQty(BigDecimal.ZERO);
        poLine.setRejectedQty(BigDecimal.ZERO);
        when(poLineMapper.selectById(100L)).thenReturn(poLine);

        when(arrivalLineMapper.updateById(any())).thenReturn(1);
        when(poLineMapper.updateById(any())).thenReturn(1);

        List<Map<String, Object>> lineResults = List.of(
                Map.of("lineId", "10", "acceptedQty", "100")
        );

        // ACCEPTED → ACCEPTED 不在状态机允许范围内 (ACCEPTED 是终态)
        assertThrows(BusinessException.class, () ->
                inspectionService.inspect(1L, "PASS", lineResults, null));
    }
}
