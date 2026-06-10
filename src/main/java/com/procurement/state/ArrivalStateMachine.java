package com.procurement.state;

import com.procurement.common.BusinessException;
import com.procurement.entity.ArrivalStatus;

import java.util.*;

/**
 * 到货单状态机
 *
 * PENDING → INSPECTING → ACCEPTED / PARTIAL_ACCEPTED / REJECTED
 */
public class ArrivalStateMachine {

    private static final Map<ArrivalStatus, Set<ArrivalStatus>> TRANSITIONS = new EnumMap<>(ArrivalStatus.class);

    static {
        TRANSITIONS.put(ArrivalStatus.PENDING, EnumSet.of(ArrivalStatus.INSPECTING, ArrivalStatus.ACCEPTED, ArrivalStatus.PARTIAL_ACCEPTED, ArrivalStatus.REJECTED));
        TRANSITIONS.put(ArrivalStatus.INSPECTING, EnumSet.of(ArrivalStatus.ACCEPTED, ArrivalStatus.PARTIAL_ACCEPTED, ArrivalStatus.REJECTED));
        TRANSITIONS.put(ArrivalStatus.ACCEPTED, EnumSet.noneOf(ArrivalStatus.class));
        TRANSITIONS.put(ArrivalStatus.PARTIAL_ACCEPTED, EnumSet.noneOf(ArrivalStatus.class));
        TRANSITIONS.put(ArrivalStatus.REJECTED, EnumSet.noneOf(ArrivalStatus.class));
    }

    public static void validateTransition(ArrivalStatus from, ArrivalStatus to) {
        Set<ArrivalStatus> allowed = TRANSITIONS.getOrDefault(from, Collections.emptySet());
        if (!allowed.contains(to)) {
            throw new BusinessException(
                    String.format("到货单状态不允许从 %s 转换到 %s", from, to));
        }
    }
}
