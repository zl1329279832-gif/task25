package com.procurement.common.enums;

import lombok.Getter;

@Getter
public enum InvoiceStatus {
    REGISTERED("已登记"),
    VERIFIED("已审核"),
    REJECTED("已驳回");

    private final String description;

    InvoiceStatus(String description) {
        this.description = description;
    }
}
