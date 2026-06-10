package com.procurement.common.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    PENDING_APPROVAL("待审批"),
    APPROVED("已审批"),
    CONFIRMED("已确认"),
    PARTIAL_DELIVERED("部分到货"),
    DELIVERED("已到货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }
}
