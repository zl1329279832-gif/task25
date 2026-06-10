package com.procurement.module.inquiry.statemachine;

import com.procurement.common.enums.InquiryStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.module.inquiry.entity.Inquiry;

import java.util.Map;
import java.util.Set;

public class InquiryStateMachine {

    private static final Map<InquiryStatus, Set<InquiryStatus>> TRANSITIONS = Map.of(
            InquiryStatus.DRAFT, Set.of(InquiryStatus.PUBLISHED, InquiryStatus.CANCELLED),
            InquiryStatus.PUBLISHED, Set.of(InquiryStatus.QUOTING, InquiryStatus.CANCELLED),
            InquiryStatus.QUOTING, Set.of(InquiryStatus.CLOSED, InquiryStatus.CANCELLED),
            InquiryStatus.CLOSED, Set.of(),
            InquiryStatus.CANCELLED, Set.of()
    );

    public static void transition(Inquiry inquiry, InquiryStatus target) {
        InquiryStatus current = InquiryStatus.valueOf(inquiry.getStatus());
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION,
                    current + " -> " + target);
        }
        inquiry.setStatus(target.name());
    }
}
