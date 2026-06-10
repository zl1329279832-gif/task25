package com.procurement.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.common.enums.InquiryStatus;
import com.procurement.module.inquiry.entity.Inquiry;
import com.procurement.module.inquiry.mapper.InquiryMapper;
import com.procurement.module.quotation.service.QuotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Freezes quotations for inquiries past their deadline.
 * Runs every 5 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotationFreezeJob {

    private final InquiryMapper inquiryMapper;
    private final QuotationService quotationService;

    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    public void freezeExpiredQuotations() {
        log.info("QuotationFreezeJob: checking for expired inquiries...");

        // Find inquiries that are PUBLISHED or QUOTING and past deadline
        LambdaQueryWrapper<Inquiry> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Inquiry::getStatus, InquiryStatus.PUBLISHED.name(), InquiryStatus.QUOTING.name())
                .isNotNull(Inquiry::getDeadline)
                .le(Inquiry::getDeadline, LocalDateTime.now());

        List<Inquiry> expiredInquiries = inquiryMapper.selectList(wrapper);

        for (Inquiry inquiry : expiredInquiries) {
            try {
                // Freeze all submitted quotations for this inquiry
                quotationService.freezeByInquiry(inquiry.getId());

                // Close the inquiry
                inquiry.setStatus(InquiryStatus.CLOSED.name());
                inquiry.setCloseTime(LocalDateTime.now());
                inquiryMapper.updateById(inquiry);

                log.info("QuotationFreezeJob: frozen quotations for inquiry {}", inquiry.getInquiryNo());
            } catch (Exception e) {
                log.error("QuotationFreezeJob: failed to freeze inquiry {}: {}", inquiry.getInquiryNo(), e.getMessage());
            }
        }

        log.info("QuotationFreezeJob: processed {} expired inquiries", expiredInquiries.size());
    }
}
