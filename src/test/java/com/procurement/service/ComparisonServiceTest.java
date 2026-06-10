package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.security.LoginUser;
import com.procurement.service.impl.ComparisonServiceImpl;
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
class ComparisonServiceTest {

    @InjectMocks
    private ComparisonServiceImpl comparisonService;

    @Mock private ComparisonMapper comparisonMapper;
    @Mock private ComparisonLineMapper comparisonLineMapper;
    @Mock private QuoteMapper quoteMapper;
    @Mock private QuoteLineMapper quoteLineMapper;
    @Mock private RfqMapper rfqMapper;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser(1L, "purchaser01", "PURCHASER", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }

    @Test
    void createComparison_lowestPrice_shouldRankByPrice() {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        when(rfqMapper.selectById(1L)).thenReturn(rfq);

        Quote q1 = new Quote();
        q1.setId(1L);
        q1.setSupplierId(1L);
        q1.setVersion(1);
        q1.setTotalAmount(new BigDecimal("1000"));
        q1.setStatus(QuoteStatus.SUBMITTED.name());

        Quote q2 = new Quote();
        q2.setId(2L);
        q2.setSupplierId(2L);
        q2.setVersion(1);
        q2.setTotalAmount(new BigDecimal("800"));
        q2.setStatus(QuoteStatus.SUBMITTED.name());

        when(quoteMapper.selectList(any())).thenReturn(List.of(q1, q2));

        QuoteLine ql1 = new QuoteLine();
        ql1.setDeliveryDays(10);
        when(quoteLineMapper.selectList(any())).thenReturn(List.of(ql1));

        when(comparisonMapper.insert(any())).thenReturn(1);
        when(comparisonMapper.updateById(any())).thenReturn(1);
        when(comparisonLineMapper.insert(any())).thenReturn(1);

        Comparison result = comparisonService.createComparison(1L, "LOWEST_PRICE");

        assertEquals("COMPLETED", result.getStatus());
        verify(comparisonLineMapper, times(2)).insert(any());
    }

    @Test
    void createComparison_noQuotes_shouldThrow() {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        when(rfqMapper.selectById(1L)).thenReturn(rfq);
        when(quoteMapper.selectList(any())).thenReturn(List.of());

        assertThrows(BusinessException.class, () ->
                comparisonService.createComparison(1L, "LOWEST_PRICE"));
    }
}
