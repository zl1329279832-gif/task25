package com.procurement.state;

import com.procurement.common.BusinessException;
import com.procurement.entity.InvoiceStatus;

import java.util.*;

/**
 * 发票状态机
 *
 * REGISTERED → VERIFIED / REJECTED
 */
public class InvoiceStateMachine {

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> TRANSITIONS = new EnumMap<>(InvoiceStatus.class);

    static {
        TRANSITIONS.put(InvoiceStatus.REGISTERED, EnumSet.of(InvoiceStatus.VERIFIED, InvoiceStatus.REJECTED));
        TRANSITIONS.put(InvoiceStatus.VERIFIED, EnumSet.noneOf(InvoiceStatus.class));
        TRANSITIONS.put(InvoiceStatus.REJECTED, EnumSet.noneOf(InvoiceStatus.class));
    }

    public static void validateTransition(InvoiceStatus from, InvoiceStatus to) {
        Set<InvoiceStatus> allowed = TRANSITIONS.getOrDefault(from, Collections.emptySet());
        if (!allowed.contains(to)) {
            throw new BusinessException(
                    String.format("发票状态不允许从 %s 转换到 %s", from, to));
        }
    }
}
