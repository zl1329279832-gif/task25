package com.procurement.module.inquiry.statemachine;

import com.procurement.common.enums.InquiryStatus;
import com.procurement.common.exception.BizException;
import com.procurement.module.inquiry.entity.Inquiry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InquiryStateMachineTest {

    @Test
    void draftToPublished_shouldSucceed() {
        Inquiry inquiry = new Inquiry();
        inquiry.setStatus(InquiryStatus.DRAFT.name());
        InquiryStateMachine.transition(inquiry, InquiryStatus.PUBLISHED);
        assertEquals(InquiryStatus.PUBLISHED.name(), inquiry.getStatus());
    }

    @Test
    void publishedToQuoting_shouldSucceed() {
        Inquiry inquiry = new Inquiry();
        inquiry.setStatus(InquiryStatus.PUBLISHED.name());
        InquiryStateMachine.transition(inquiry, InquiryStatus.QUOTING);
        assertEquals(InquiryStatus.QUOTING.name(), inquiry.getStatus());
    }

    @Test
    void quotingToClosed_shouldSucceed() {
        Inquiry inquiry = new Inquiry();
        inquiry.setStatus(InquiryStatus.QUOTING.name());
        InquiryStateMachine.transition(inquiry, InquiryStatus.CLOSED);
        assertEquals(InquiryStatus.CLOSED.name(), inquiry.getStatus());
    }

    @Test
    void draftToCancelled_shouldSucceed() {
        Inquiry inquiry = new Inquiry();
        inquiry.setStatus(InquiryStatus.DRAFT.name());
        InquiryStateMachine.transition(inquiry, InquiryStatus.CANCELLED);
        assertEquals(InquiryStatus.CANCELLED.name(), inquiry.getStatus());
    }

    @Test
    void closedToPublished_shouldThrow() {
        Inquiry inquiry = new Inquiry();
        inquiry.setStatus(InquiryStatus.CLOSED.name());
        assertThrows(BizException.class, () ->
                InquiryStateMachine.transition(inquiry, InquiryStatus.PUBLISHED));
    }

    @Test
    void draftToQuoting_shouldThrow() {
        Inquiry inquiry = new Inquiry();
        inquiry.setStatus(InquiryStatus.DRAFT.name());
        assertThrows(BizException.class, () ->
                InquiryStateMachine.transition(inquiry, InquiryStatus.QUOTING));
    }

    @Test
    void cancelledToAny_shouldThrow() {
        Inquiry inquiry = new Inquiry();
        inquiry.setStatus(InquiryStatus.CANCELLED.name());
        assertThrows(BizException.class, () ->
                InquiryStateMachine.transition(inquiry, InquiryStatus.DRAFT));
    }
}
