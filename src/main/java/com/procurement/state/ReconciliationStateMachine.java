package com.procurement.state;

import com.procurement.common.BusinessException;
import com.procurement.entity.ReconciliationStatus;

import java.util.*;

/**
 * 对账单状态机
 *
 * PENDING → MATCHED / DIFFERENT
 * MATCHED → APPROVED
 * DIFFERENT → APPROVED / REJECTED
 */
public class ReconciliationStateMachine {

    private static final Map<ReconciliationStatus, Set<ReconciliationStatus>> TRANSITIONS = new EnumMap<>(ReconciliationStatus.class);

    static {
        TRANSITIONS.put(ReconciliationStatus.PENDING, EnumSet.of(ReconciliationStatus.MATCHED, ReconciliationStatus.DIFFERENT));
        TRANSITIONS.put(ReconciliationStatus.MATCHED, EnumSet.of(ReconciliationStatus.APPROVED));
        TRANSITIONS.put(ReconciliationStatus.DIFFERENT, EnumSet.of(ReconciliationStatus.APPROVED, ReconciliationStatus.REJECTED));
        TRANSITIONS.put(ReconciliationStatus.APPROVED, EnumSet.noneOf(ReconciliationStatus.class));
        TRANSITIONS.put(ReconciliationStatus.REJECTED, EnumSet.noneOf(ReconciliationStatus.class));
    }

    public static void validateTransition(ReconciliationStatus from, ReconciliationStatus to) {
        Set<ReconciliationStatus> allowed = TRANSITIONS.getOrDefault(from, Collections.emptySet());
        if (!allowed.contains(to)) {
            throw new BusinessException(
                    String.format("对账单状态不允许从 %s 转换到 %s", from, to));
        }
    }
}
