package com.procurement.service;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import com.procurement.mapper.*;
import com.procurement.service.impl.QuoteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @InjectMocks
    private QuoteServiceImpl quoteService;

    @Mock
    private QuoteMapper quoteMapper;

    @Mock
    private QuoteLineMapper quoteLineMapper;

    @Mock
    private RfqMapper rfqMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void submitQuote_shouldRejectAfterDeadline() {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setStatus(RfqStatus.PUBLISHED.name());
        rfq.setDeadline(LocalDateTime.now().minusHours(1)); // 已过期
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(rfqMapper.selectById(1L)).thenReturn(rfq);

        QuoteLine line = new QuoteLine();
        line.setUnitPrice(new BigDecimal("100"));
        line.setQuantity(new BigDecimal("10"));

        assertThrows(BusinessException.class, () ->
                quoteService.submitQuote(1L, 1L, List.of(line)));
    }

    @Test
    void submitQuote_shouldCreateFirstVersion() {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setStatus(RfqStatus.PUBLISHED.name());
        rfq.setDeadline(LocalDateTime.now().plusDays(7));
        when(rfqMapper.selectById(1L)).thenReturn(rfq);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(quoteMapper.selectOne(any())).thenReturn(null); // 无现有报价
        when(quoteMapper.insert(any())).thenReturn(1);
        when(quoteLineMapper.insert(any())).thenReturn(1);

        QuoteLine line = new QuoteLine();
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setQuantity(new BigDecimal("10"));
        line.setMaterialId(1L);

        Quote result = quoteService.submitQuote(1L, 1L, List.of(line));

        assertEquals(1, result.getVersion());
        assertEquals(new BigDecimal("1000.00"), result.getTotalAmount());
        assertEquals(QuoteStatus.SUBMITTED.name(), result.getStatus());
    }

    @Test
    void submitQuote_shouldIncrementVersion() {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setStatus(RfqStatus.PUBLISHED.name());
        rfq.setDeadline(LocalDateTime.now().plusDays(7));
        when(rfqMapper.selectById(1L)).thenReturn(rfq);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        Quote existing = new Quote();
        existing.setQuoteNo("QT-001");
        existing.setVersion(2);
        existing.setFrozen(0);
        when(quoteMapper.selectOne(any())).thenReturn(existing);
        when(quoteMapper.insert(any())).thenReturn(1);
        when(quoteLineMapper.insert(any())).thenReturn(1);

        QuoteLine line = new QuoteLine();
        line.setUnitPrice(new BigDecimal("95.00"));
        line.setQuantity(new BigDecimal("10"));
        line.setMaterialId(1L);

        Quote result = quoteService.submitQuote(1L, 1L, List.of(line));

        assertEquals(3, result.getVersion());
    }

    @Test
    void submitQuote_shouldRejectWhenFrozen() {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setStatus(RfqStatus.PUBLISHED.name());
        rfq.setDeadline(LocalDateTime.now().plusDays(7));
        when(rfqMapper.selectById(1L)).thenReturn(rfq);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        Quote existing = new Quote();
        existing.setQuoteNo("QT-001");
        existing.setVersion(1);
        existing.setFrozen(1); // 已冻结
        when(quoteMapper.selectOne(any())).thenReturn(existing);

        QuoteLine line = new QuoteLine();
        line.setUnitPrice(new BigDecimal("100"));
        line.setQuantity(new BigDecimal("10"));

        assertThrows(BusinessException.class, () ->
                quoteService.submitQuote(1L, 1L, List.of(line)));
    }

    @Test
    void freezeQuote_shouldSetFrozenFlag() {
        Quote quote = new Quote();
        quote.setId(1L);
        quote.setFrozen(0);
        quote.setStatus(QuoteStatus.SUBMITTED.name());
        when(quoteMapper.selectById(1L)).thenReturn(quote);
        when(quoteMapper.updateById(any())).thenReturn(1);

        quoteService.freezeQuote(1L);

        assertEquals(1, quote.getFrozen());
        assertEquals(QuoteStatus.FROZEN.name(), quote.getStatus());
    }

    @Test
    void freezeAllByRfq_shouldFreezeAllActiveQuotes() {
        Quote q1 = new Quote();
        q1.setId(1L);
        q1.setFrozen(0);
        Quote q2 = new Quote();
        q2.setId(2L);
        q2.setFrozen(0);

        when(quoteMapper.selectList(any())).thenReturn(List.of(q1, q2));
        when(quoteMapper.updateById(any())).thenReturn(1);

        quoteService.freezeAllByRfq(1L);

        verify(quoteMapper, times(2)).updateById(any());
        assertEquals(1, q1.getFrozen());
        assertEquals(1, q2.getFrozen());
    }

    @Test
    void submitQuote_shouldRejectWhenRfqClosed() {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setStatus(RfqStatus.CLOSED.name());
        rfq.setDeadline(LocalDateTime.now().plusDays(7));
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(rfqMapper.selectById(1L)).thenReturn(rfq);

        QuoteLine line = new QuoteLine();
        line.setUnitPrice(new BigDecimal("100"));
        line.setQuantity(new BigDecimal("10"));

        assertThrows(BusinessException.class, () ->
                quoteService.submitQuote(1L, 1L, List.of(line)));
    }

    @Test
    void submitQuote_shouldRejectWhenRfqCancelled() {
        Rfq rfq = new Rfq();
        rfq.setId(1L);
        rfq.setStatus(RfqStatus.CANCELLED.name());
        rfq.setDeadline(LocalDateTime.now().plusDays(7));
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        when(rfqMapper.selectById(1L)).thenReturn(rfq);

        QuoteLine line = new QuoteLine();
        line.setUnitPrice(new BigDecimal("100"));
        line.setQuantity(new BigDecimal("10"));

        assertThrows(BusinessException.class, () ->
                quoteService.submitQuote(1L, 1L, List.of(line)));
    }
}
