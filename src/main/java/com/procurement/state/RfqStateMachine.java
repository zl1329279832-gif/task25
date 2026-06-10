package com.procurement.state;

import com.procurement.common.BusinessException;
import com.procurement.entity.RfqStatus;

import java.util.*;

/**
 * 询价单状态机
 *
 * DRAFT → PUBLISHED → CLOSED（报价截止后自动关闭）
 *       → CANCELLED
 */
public class RfqStateMachine {

    private static final Map<RfqStatus, Set<RfqStatus>> TRANSITIONS = new EnumMap<>(RfqStatus.class);

    static {
        TRANSITIONS.put(RfqStatus.DRAFT, EnumSet.of(RfqStatus.PUBLISHED, RfqStatus.CANCELLED));
        TRANSITIONS.put(RfqStatus.PUBLISHED, EnumSet.of(RfqStatus.CLOSED, RfqStatus.CANCELLED));
        TRANSITIONS.put(RfqStatus.CLOSED, EnumSet.noneOf(RfqStatus.class));
        TRANSITIONS.put(RfqStatus.CANCELLED, EnumSet.noneOf(RfqStatus.class));
    }

    public static void validateTransition(RfqStatus from, RfqStatus to) {
        Set<RfqStatus> allowed = TRANSITIONS.getOrDefault(from, Collections.emptySet());
        if (!allowed.contains(to)) {
            throw new BusinessException(
                    String.format("询价单状态不允许从 %s 转换到 %s", from, to));
        }
    }
}
