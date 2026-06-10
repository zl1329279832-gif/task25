package com.procurement.module.quotation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.common.enums.InquiryStatus;
import com.procurement.common.enums.QuotationStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.util.CodeGenerator;
import com.procurement.module.inquiry.entity.Inquiry;
import com.procurement.module.inquiry.entity.InquirySupplier;
import com.procurement.module.inquiry.mapper.InquiryMapper;
import com.procurement.module.inquiry.mapper.InquirySupplierMapper;
import com.procurement.module.inquiry.service.InquiryService;
import com.procurement.module.quotation.dto.QuotationSubmitDTO;
import com.procurement.module.quotation.entity.Quotation;
import com.procurement.module.quotation.mapper.QuotationItemMapper;
import com.procurement.module.quotation.mapper.QuotationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationServiceTest {

    @Mock private QuotationMapper quotationMapper;
    @Mock private QuotationItemMapper itemMapper;
    @Mock private InquiryMapper inquiryMapper;
    @Mock private InquirySupplierMapper inquirySupplierMapper;
    @Mock private InquiryService inquiryService;
    @Mock private CodeGenerator codeGenerator;

    @InjectMocks
    private QuotationService service;

    @Test
    void submit_shouldThrow_whenDeadlinePassed() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(1L);
        inquiry.setStatus(InquiryStatus.QUOTING.name());
        inquiry.setDeadline(LocalDateTime.now().minusDays(1)); // past deadline
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);

        QuotationSubmitDTO dto = new QuotationSubmitDTO();
        dto.setInquiryId(1L);
        dto.setItems(Collections.emptyList());

        assertThrows(BizException.class, () -> service.submit(dto, 1L));
    }

    @Test
    void submit_shouldThrow_whenSupplierNotInvited() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(1L);
        inquiry.setStatus(InquiryStatus.PUBLISHED.name());
        inquiry.setDeadline(LocalDateTime.now().plusDays(1));
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);
        when(inquirySupplierMapper.selectCount(any())).thenReturn(0L);

        QuotationSubmitDTO dto = new QuotationSubmitDTO();
        dto.setInquiryId(1L);
        dto.setItems(Collections.emptyList());

        assertThrows(BizException.class, () -> service.submit(dto, 99L));
    }

    @Test
    void submit_shouldThrow_whenQuotationAlreadyFrozen() {
        Inquiry inquiry = new Inquiry();
        inquiry.setId(1L);
        inquiry.setStatus(InquiryStatus.QUOTING.name());
        inquiry.setDeadline(LocalDateTime.now().plusDays(1));
        when(inquiryMapper.selectById(1L)).thenReturn(inquiry);
        when(inquirySupplierMapper.selectCount(any())).thenReturn(1L);

        Quotation existingFrozen = new Quotation();
        existingFrozen.setStatus(QuotationStatus.FROZEN.name());
        existingFrozen.setVersion(1);
        when(quotationMapper.selectOne(any())).thenReturn(existingFrozen);

        QuotationSubmitDTO dto = new QuotationSubmitDTO();
        dto.setInquiryId(1L);
        dto.setItems(Collections.emptyList());

        assertThrows(BizException.class, () -> service.submit(dto, 1L));
    }

    @Test
    void freezeByInquiry_shouldFreezeAllSubmittedQuotations() {
        Quotation q1 = new Quotation();
        q1.setId(1L);
        q1.setStatus(QuotationStatus.SUBMITTED.name());
        Quotation q2 = new Quotation();
        q2.setId(2L);
        q2.setStatus(QuotationStatus.SUBMITTED.name());

        when(quotationMapper.selectList(any())).thenReturn(List.of(q1, q2));
        when(quotationMapper.updateById(any())).thenReturn(1);

        service.freezeByInquiry(1L);

        assertEquals(QuotationStatus.FROZEN.name(), q1.getStatus());
        assertEquals(QuotationStatus.FROZEN.name(), q2.getStatus());
        assertNotNull(q1.getFrozenTime());
        assertNotNull(q2.getFrozenTime());
        verify(quotationMapper, times(2)).updateById(any());
    }
}
