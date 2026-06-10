package com.procurement.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeGeneratorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private CodeGenerator codeGenerator;

    @Test
    void generate_shouldReturnFormattedCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        String code = codeGenerator.generate("PO");
        String expectedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertEquals("PO-" + expectedDate + "-0001", code);
    }

    @Test
    void generate_shouldIncrementSequence() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(42L);

        String code = codeGenerator.generate("INQ");
        assertTrue(code.endsWith("-0042"));
        assertTrue(code.startsWith("INQ-"));
    }

    @Test
    void generate_shouldSetExpireOnFirstCall() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        codeGenerator.generate("PO");
        verify(redisTemplate).expire(anyString(), eq(24L), any());
    }

    @Test
    void generate_shouldNotSetExpireOnSubsequentCalls() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(5L);

        codeGenerator.generate("PO");
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }
}
