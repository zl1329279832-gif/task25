package com.procurement.state;

import com.procurement.common.BusinessException;
import com.procurement.entity.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateMachineTest {

    // ========== QuoteStateMachine ==========

    @Test
    void quoteStateMachine_validTransitions() {
        assertDoesNotThrow(() ->
                QuoteStateMachine.validateTransition(QuoteStatus.DRAFT, QuoteStatus.SUBMITTED));
        assertDoesNotThrow(() ->
                QuoteStateMachine.validateTransition(QuoteStatus.SUBMITTED, QuoteStatus.FROZEN));
        assertDoesNotThrow(() ->
                QuoteStateMachine.validateTransition(QuoteStatus.SUBMITTED, QuoteStatus.ACCEPTED));
        assertDoesNotThrow(() ->
                QuoteStateMachine.validateTransition(QuoteStatus.FROZEN, QuoteStatus.ACCEPTED));
        assertDoesNotThrow(() ->
                QuoteStateMachine.validateTransition(QuoteStatus.FROZEN, QuoteStatus.REJECTED));
    }

    @Test
    void quoteStateMachine_invalidTransitions() {
        assertThrows(BusinessException.class, () ->
                QuoteStateMachine.validateTransition(QuoteStatus.ACCEPTED, QuoteStatus.FROZEN));
        assertThrows(BusinessException.class, () ->
                QuoteStateMachine.validateTransition(QuoteStatus.REJECTED, QuoteStatus.SUBMITTED));
        assertThrows(BusinessException.class, () ->
                QuoteStateMachine.validateTransition(QuoteStatus.DRAFT, QuoteStatus.FROZEN));
    }

    // ========== ArrivalStateMachine ==========

    @Test
    void arrivalStateMachine_validTransitions() {
        assertDoesNotThrow(() ->
                ArrivalStateMachine.validateTransition(ArrivalStatus.PENDING, ArrivalStatus.INSPECTING));
        assertDoesNotThrow(() ->
                ArrivalStateMachine.validateTransition(ArrivalStatus.PENDING, ArrivalStatus.ACCEPTED));
        assertDoesNotThrow(() ->
                ArrivalStateMachine.validateTransition(ArrivalStatus.PENDING, ArrivalStatus.PARTIAL_ACCEPTED));
        assertDoesNotThrow(() ->
                ArrivalStateMachine.validateTransition(ArrivalStatus.PENDING, ArrivalStatus.REJECTED));
        assertDoesNotThrow(() ->
                ArrivalStateMachine.validateTransition(ArrivalStatus.INSPECTING, ArrivalStatus.ACCEPTED));
        assertDoesNotThrow(() ->
                ArrivalStateMachine.validateTransition(ArrivalStatus.INSPECTING, ArrivalStatus.PARTIAL_ACCEPTED));
        assertDoesNotThrow(() ->
                ArrivalStateMachine.validateTransition(ArrivalStatus.INSPECTING, ArrivalStatus.REJECTED));
    }

    @Test
    void arrivalStateMachine_invalidTransitions() {
        assertThrows(BusinessException.class, () ->
                ArrivalStateMachine.validateTransition(ArrivalStatus.ACCEPTED, ArrivalStatus.REJECTED));
        assertThrows(BusinessException.class, () ->
                ArrivalStateMachine.validateTransition(ArrivalStatus.REJECTED, ArrivalStatus.ACCEPTED));
        assertThrows(BusinessException.class, () ->
                ArrivalStateMachine.validateTransition(ArrivalStatus.PARTIAL_ACCEPTED, ArrivalStatus.ACCEPTED));
    }

    // ========== InvoiceStateMachine ==========

    @Test
    void invoiceStateMachine_validTransitions() {
        assertDoesNotThrow(() ->
                InvoiceStateMachine.validateTransition(InvoiceStatus.REGISTERED, InvoiceStatus.VERIFIED));
        assertDoesNotThrow(() ->
                InvoiceStateMachine.validateTransition(InvoiceStatus.REGISTERED, InvoiceStatus.REJECTED));
    }

    @Test
    void invoiceStateMachine_invalidTransitions() {
        assertThrows(BusinessException.class, () ->
                InvoiceStateMachine.validateTransition(InvoiceStatus.VERIFIED, InvoiceStatus.REJECTED));
        assertThrows(BusinessException.class, () ->
                InvoiceStateMachine.validateTransition(InvoiceStatus.REJECTED, InvoiceStatus.VERIFIED));
        assertThrows(BusinessException.class, () ->
                InvoiceStateMachine.validateTransition(InvoiceStatus.REGISTERED, InvoiceStatus.REGISTERED));
    }

    // ========== ReconciliationStateMachine ==========

    @Test
    void reconciliationStateMachine_validTransitions() {
        assertDoesNotThrow(() ->
                ReconciliationStateMachine.validateTransition(ReconciliationStatus.PENDING, ReconciliationStatus.MATCHED));
        assertDoesNotThrow(() ->
                ReconciliationStateMachine.validateTransition(ReconciliationStatus.PENDING, ReconciliationStatus.DIFFERENT));
        assertDoesNotThrow(() ->
                ReconciliationStateMachine.validateTransition(ReconciliationStatus.MATCHED, ReconciliationStatus.APPROVED));
        assertDoesNotThrow(() ->
                ReconciliationStateMachine.validateTransition(ReconciliationStatus.DIFFERENT, ReconciliationStatus.APPROVED));
        assertDoesNotThrow(() ->
                ReconciliationStateMachine.validateTransition(ReconciliationStatus.DIFFERENT, ReconciliationStatus.REJECTED));
    }

    @Test
    void reconciliationStateMachine_invalidTransitions() {
        assertThrows(BusinessException.class, () ->
                ReconciliationStateMachine.validateTransition(ReconciliationStatus.APPROVED, ReconciliationStatus.REJECTED));
        assertThrows(BusinessException.class, () ->
                ReconciliationStateMachine.validateTransition(ReconciliationStatus.REJECTED, ReconciliationStatus.APPROVED));
        assertThrows(BusinessException.class, () ->
                ReconciliationStateMachine.validateTransition(ReconciliationStatus.MATCHED, ReconciliationStatus.DIFFERENT));
    }
}
