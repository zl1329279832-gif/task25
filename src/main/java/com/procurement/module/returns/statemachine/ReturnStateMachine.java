package com.procurement.module.returns.statemachine;

import com.procurement.common.enums.ReturnStatus;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ReturnStateMachine {

    private static final Map<ReturnStatus, Set<ReturnStatus>> TRANSITIONS = Map.of(
            ReturnStatus.PENDING, Set.of(ReturnStatus.SUPPLIER_CONFIRMED),
            ReturnStatus.SUPPLIER_CONFIRMED, Set.of(ReturnStatus.RETURNING),
            ReturnStatus.RETURNING, Set.of(ReturnStatus.COMPLETED)
    );

    public void validateTransition(ReturnStatus from, ReturnStatus to) {
        Set<ReturnStatus> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BizException(ErrorCode.RETURN_STATUS_ERROR);
        }
    }
}
