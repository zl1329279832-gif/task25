package com.procurement.common.enums;

import lombok.Getter;

@Getter
public enum ReturnStatus {
    PENDING("待确认"),
    SUPPLIER_CONFIRMED("供应商已确认"),
    RETURNING("退货中"),
    COMPLETED("已完成");

    private final String description;

    ReturnStatus(String description) {
        this.description = description;
    }
}
