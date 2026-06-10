package com.procurement.module.order.statemachine;

import com.procurement.common.enums.OrderStatus;
import com.procurement.common.exception.BizException;
import com.procurement.module.order.entity.PurchaseOrder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {

    @Test
    void pendingToApproved_shouldSucceed() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(OrderStatus.PENDING_APPROVAL.name());
        OrderStateMachine.transition(order, OrderStatus.APPROVED);
        assertEquals(OrderStatus.APPROVED.name(), order.getStatus());
    }

    @Test
    void approvedToConfirmed_shouldSucceed() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(OrderStatus.APPROVED.name());
        OrderStateMachine.transition(order, OrderStatus.CONFIRMED);
        assertEquals(OrderStatus.CONFIRMED.name(), order.getStatus());
    }

    @Test
    void confirmedToPartialDelivered_shouldSucceed() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(OrderStatus.CONFIRMED.name());
        OrderStateMachine.transition(order, OrderStatus.PARTIAL_DELIVERED);
        assertEquals(OrderStatus.PARTIAL_DELIVERED.name(), order.getStatus());
    }

    @Test
    void partialDeliveredToDelivered_shouldSucceed() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(OrderStatus.PARTIAL_DELIVERED.name());
        OrderStateMachine.transition(order, OrderStatus.DELIVERED);
        assertEquals(OrderStatus.DELIVERED.name(), order.getStatus());
    }

    @Test
    void deliveredToCompleted_shouldSucceed() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(OrderStatus.DELIVERED.name());
        OrderStateMachine.transition(order, OrderStatus.COMPLETED);
        assertEquals(OrderStatus.COMPLETED.name(), order.getStatus());
    }

    @Test
    void approvedToCancelled_shouldSucceed() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(OrderStatus.APPROVED.name());
        OrderStateMachine.transition(order, OrderStatus.CANCELLED);
        assertEquals(OrderStatus.CANCELLED.name(), order.getStatus());
    }

    @Test
    void deliveredToCancelled_shouldThrow_cannotCancelAfterDelivery() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(OrderStatus.DELIVERED.name());
        assertThrows(BizException.class, () ->
                OrderStateMachine.transition(order, OrderStatus.CANCELLED));
    }

    @Test
    void completedToAny_shouldThrow_terminalState() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(OrderStatus.COMPLETED.name());
        assertThrows(BizException.class, () ->
                OrderStateMachine.transition(order, OrderStatus.APPROVED));
    }

    @Test
    void pendingToDelivered_shouldThrow_skipSteps() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus(OrderStatus.PENDING_APPROVAL.name());
        assertThrows(BizException.class, () ->
                OrderStateMachine.transition(order, OrderStatus.DELIVERED));
    }
}
