package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.impl.RfqServiceImpl;
import com.procurement.state.RfqStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RfqServiceTest {

    @InjectMocks
    private RfqServiceImpl rfqService;

    @Mock
    private RfqMapper rfqMapper;

    @Mock
    private RfqLineMapper rfqLineMapper;

    @Mock
    private RfqSupplierMapper rfqSupplierMapper;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(1L, "purchaser01", "PURCHASER", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    @Test
    void create_shouldSetDraftStatus() {
        Rfq rfq = new Rfq();
        rfq.setTitle("Test RFQ");
        rfq.setDeadline(LocalDateTime.now().plusDays(7));

        RfqLine line = new RfqLine();
        line.setMaterialId(1L);

        when(rfqMapper.insert(any())).thenReturn(1);
        when(rfqLineMapper.insert(any())).thenReturn(1);
        when(rfqSupplierMapper.insert(any())).thenReturn(1);

        Rfq result = rfqService.create(rfq, List.of(line), List.of(1L, 2L));

        assertEquals(RfqStatus.DRAFT.name(), result.getStatus());
        assertEquals(1L, result.getPurchaserId());
        verify(rfqSupplierMapper, times(2)).insert(any());
    }

    @Test
    void publish_shouldTransitionFromDraft() {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setStatus(RfqStatus.DRAFT.name());
        when(rfqMapper.selectById(1L)).thenReturn(rfq);
        when(rfqMapper.updateById(any())).thenReturn(1);

        Rfq result = rfqService.publish(1L);

        assertEquals(RfqStatus.PUBLISHED.name(), result.getStatus());
    }

    @Test
    void close_shouldRejectFromDraft() {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setStatus(RfqStatus.DRAFT.name());
        when(rfqMapper.selectById(1L)).thenReturn(rfq);

        assertThrows(BusinessException.class, () -> rfqService.close(1L));
    }

    @Test
    void rfqStateMachine_shouldValidateTransitions() {
        // Valid
        assertDoesNotThrow(() ->
                RfqStateMachine.validateTransition(RfqStatus.DRAFT, RfqStatus.PUBLISHED));
        assertDoesNotThrow(() ->
                RfqStateMachine.validateTransition(RfqStatus.PUBLISHED, RfqStatus.CLOSED));

        // Invalid
        assertThrows(BusinessException.class, () ->
                RfqStateMachine.validateTransition(RfqStatus.CLOSED, RfqStatus.PUBLISHED));
        assertThrows(BusinessException.class, () ->
                RfqStateMachine.validateTransition(RfqStatus.DRAFT, RfqStatus.CLOSED));
    }
}
