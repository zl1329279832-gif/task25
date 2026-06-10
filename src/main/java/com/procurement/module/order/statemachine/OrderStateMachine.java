package com.procurement.module.order.statemachine;

import com.procurement.common.enums.OrderStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.module.order.entity.PurchaseOrder;

import java.util.Map;
import java.util.Set;

public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PENDING_APPROVAL, Set.of(OrderStatus.APPROVED, OrderStatus.CANCELLED),
            OrderStatus.APPROVED, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.PARTIAL_DELIVERED, OrderStatus.CANCELLED),
            OrderStatus.PARTIAL_DELIVERED, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(OrderStatus.COMPLETED),
            OrderStatus.COMPLETED, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    public static void transition(PurchaseOrder order, OrderStatus target) {
        OrderStatus current = OrderStatus.valueOf(order.getStatus());
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION,
                    current + " -> " + target);
        }
        order.setStatus(target.name());
    }
}
