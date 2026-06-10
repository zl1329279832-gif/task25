package com.procurement.state;

import com.procurement.common.BusinessException;
import com.procurement.entity.QuoteStatus;

import java.util.*;

/**
 * 报价单状态机
 *
 * DRAFT → SUBMITTED → ACCEPTED / REJECTED / FROZEN
 *                     FROZEN → ACCEPTED / REJECTED
 */
public class QuoteStateMachine {

    private static final Map<QuoteStatus, Set<QuoteStatus>> TRANSITIONS = new EnumMap<>(QuoteStatus.class);

    static {
        TRANSITIONS.put(QuoteStatus.DRAFT, EnumSet.of(QuoteStatus.SUBMITTED));
        TRANSITIONS.put(QuoteStatus.SUBMITTED, EnumSet.of(QuoteStatus.ACCEPTED, QuoteStatus.REJECTED, QuoteStatus.FROZEN));
        TRANSITIONS.put(QuoteStatus.FROZEN, EnumSet.of(QuoteStatus.ACCEPTED, QuoteStatus.REJECTED));
        TRANSITIONS.put(QuoteStatus.ACCEPTED, EnumSet.noneOf(QuoteStatus.class));
        TRANSITIONS.put(QuoteStatus.REJECTED, EnumSet.noneOf(QuoteStatus.class));
    }

    public static void validateTransition(QuoteStatus from, QuoteStatus to) {
        Set<QuoteStatus> allowed = TRANSITIONS.getOrDefault(from, Collections.emptySet());
        if (!allowed.contains(to)) {
            throw new BusinessException(
                    String.format("报价单状态不允许从 %s 转换到 %s", from, to));
        }
    }
}
