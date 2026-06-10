package com.procurement.state;

import com.procurement.common.BusinessException;
import com.procurement.entity.PoStatus;

import java.util.*;

/**
 * 采购订单状态机
 *
 * DRAFT → PENDING_APPROVAL → APPROVED → CONFIRMED → PARTIAL_RECEIVED → RECEIVED
 *                           → REJECTED
 *        (any non-received) → CANCELLED
 *
 * 规则：已收货部分（PARTIAL_RECEIVED / RECEIVED）不允许取消
 */
public class PurchaseOrderStateMachine {

    private static final Map<PoStatus, Set<PoStatus>> TRANSITIONS = new EnumMap<>(PoStatus.class);

    static {
        TRANSITIONS.put(PoStatus.DRAFT, EnumSet.of(PoStatus.PENDING_APPROVAL, PoStatus.CANCELLED));
        TRANSITIONS.put(PoStatus.PENDING_APPROVAL, EnumSet.of(PoStatus.APPROVED, PoStatus.REJECTED, PoStatus.CANCELLED));
        TRANSITIONS.put(PoStatus.APPROVED, EnumSet.of(PoStatus.CONFIRMED, PoStatus.CANCELLED));
        TRANSITIONS.put(PoStatus.REJECTED, EnumSet.of(PoStatus.DRAFT));
        TRANSITIONS.put(PoStatus.CONFIRMED, EnumSet.of(PoStatus.PARTIAL_RECEIVED, PoStatus.RECEIVED, PoStatus.CANCELLED));
        TRANSITIONS.put(PoStatus.PARTIAL_RECEIVED, EnumSet.of(PoStatus.RECEIVED));
        TRANSITIONS.put(PoStatus.RECEIVED, EnumSet.noneOf(PoStatus.class));
        TRANSITIONS.put(PoStatus.CANCELLED, EnumSet.noneOf(PoStatus.class));
    }

    public static void validateTransition(PoStatus from, PoStatus to) {
        Set<PoStatus> allowed = TRANSITIONS.getOrDefault(from, Collections.emptySet());
        if (!allowed.contains(to)) {
            throw new BusinessException(
                    String.format("采购订单状态不允许从 %s 转换到 %s", from, to));
        }
    }

    public static boolean canCancel(PoStatus current) {
        return current != PoStatus.PARTIAL_RECEIVED
                && current != PoStatus.RECEIVED
                && current != PoStatus.CANCELLED;
    }
}
