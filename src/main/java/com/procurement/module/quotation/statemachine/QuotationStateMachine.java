package com.procurement.module.quotation.statemachine;

import com.procurement.common.enums.QuotationStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.module.quotation.entity.Quotation;

import java.util.Map;
import java.util.Set;

public class QuotationStateMachine {

    private static final Map<QuotationStatus, Set<QuotationStatus>> TRANSITIONS = Map.of(
            QuotationStatus.DRAFT, Set.of(QuotationStatus.SUBMITTED),
            QuotationStatus.SUBMITTED, Set.of(QuotationStatus.FROZEN),
            QuotationStatus.FROZEN, Set.of(QuotationStatus.SELECTED, QuotationStatus.REJECTED),
            QuotationStatus.SELECTED, Set.of(),
            QuotationStatus.REJECTED, Set.of()
    );

    public static void transition(Quotation quotation, QuotationStatus target) {
        QuotationStatus current = QuotationStatus.valueOf(quotation.getStatus());
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION,
                    current + " -> " + target);
        }
        quotation.setStatus(target.name());
    }
}
