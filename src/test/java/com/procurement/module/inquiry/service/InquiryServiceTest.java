package com.procurement.module.inquiry.service;

import com.procurement.common.enums.InquiryStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.inquiry.dto.InquiryCreateDTO;
import com.procurement.module.inquiry.entity.Inquiry;
import com.procurement.module.inquiry.mapper.InquiryItemMapper;
import com.procurement.module.inquiry.mapper.InquiryMapper;
import com.procurement.module.inquiry.mapper.InquirySupplierMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock private InquiryMapper inquiryMapper;
    @Mock private InquiryItemMapper itemMapper;
    @Mock private InquirySupplierMapper supplierMapper;
    @Mock private CodeGenerator codeGenerator;

    @InjectMocks
    private InquiryService service;

    @Test
    void create_shouldCreateDraftInquiry() {
        when(codeGenerator.generate("INQ")).thenReturn("INQ-20260101-0001");
        when(inquiryMapper.insert(any())).thenReturn(1);

        InquiryCreateDTO dto = new InquiryCreateDTO();
        dto.setTitle("Test Inquiry");
        dto.setDeadline(LocalDateTime.now().plusDays(7));
        dto.setItems(Collections.emptyList());

        service.create(dto, 1L);

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryMapper).insert(captor.capture());
        assertEquals(InquiryStatus.DRAFT.name(), captor.getValue().getStatus());
        assertEquals(1L, captor.getValue().getBuyerId());
    }

    @Test
    void publish_shouldThrow_whenDeadlineNotSet() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(1L);
        inquiry.setStatus(InquiryStatus.DRAFT.name());
        inquiry.setDeadline(null);
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);

        assertThrows(BizException.class, () -> service.publish(1L));
    }

    @Test
    void publish_shouldSucceed_whenDeadlineSet() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(1L);
        inquiry.setStatus(InquiryStatus.DRAFT.name());
        inquiry.setDeadline(LocalDateTime.now().plusDays(7));
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);
        when(inquiryMapper.updateById(any())).thenReturn(1);

        assertDoesNotThrow(() -> service.publish(1L));
        assertEquals(InquiryStatus.PUBLISHED.name(), inquiry.getStatus());
    }

    @Test
    void close_shouldThrow_whenStatusIsDraft() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(1L);
        inquiry.setStatus(InquiryStatus.DRAFT.name());
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);

        assertThrows(BizException.class, () -> service.close(1L));
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(inquiryMapper.selectById(999L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.getById(999L));
    }
}
