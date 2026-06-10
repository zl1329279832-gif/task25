package com.procurement.module.quotation.statemachine;

import com.procurement.common.enums.QuotationStatus;
import com.procurement.common.exception.BizException;
import com.procurement.module.quotation.entity.Quotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuotationStateMachineTest {

    @Test
    void submittedToFrozen_shouldSucceed() {
        Quotation q = new Quotation();
        q.setStatus(QuotationStatus.SUBMITTED.name());
        QuotationStateMachine.transition(q, QuotationStatus.FROZEN);
        assertEquals(QuotationStatus.FROZEN.name(), q.getStatus());
    }

    @Test
    void frozenToSelected_shouldSucceed() {
        Quotation q = new Quotation();
        q.setStatus(QuotationStatus.FROZEN.name());
        QuotationStateMachine.transition(q, QuotationStatus.SELECTED);
        assertEquals(QuotationStatus.SELECTED.name(), q.getStatus());
    }

    @Test
    void frozenToRejected_shouldSucceed() {
        Quotation q = new Quotation();
        q.setStatus(QuotationStatus.FROZEN.name());
        QuotationStateMachine.transition(q, QuotationStatus.REJECTED);
        assertEquals(QuotationStatus.REJECTED.name(), q.getStatus());
    }

    @Test
    void submittedToSelected_shouldThrow_mustFreezeFirst() {
        Quotation q = new Quotation();
        q.setStatus(QuotationStatus.SUBMITTED.name());
        assertThrows(BizException.class, () ->
                QuotationStateMachine.transition(q, QuotationStatus.SELECTED));
    }

    @Test
    void frozenToSubmitted_shouldThrow_noRollback() {
        Quotation q = new Quotation();
        q.setStatus(QuotationStatus.FROZEN.name());
        assertThrows(BizException.class, () ->
                QuotationStateMachine.transition(q, QuotationStatus.SUBMITTED));
    }

    @Test
    void selectedToAny_shouldThrow_terminalState() {
        Quotation q = new Quotation();
        q.setStatus(QuotationStatus.SELECTED.name());
        assertThrows(BizException.class, () ->
                QuotationStateMachine.transition(q, QuotationStatus.FROZEN));
    }
}
