package com.procurement.common.enums;

import lombok.Getter;

@Getter
public enum ReconciliationStatus {
    DRAFT("草稿"),
    CONFIRMED("已确认"),
    DISPUTED("有争议"),
    SETTLED("已结算");

    private final String description;

    ReconciliationStatus(String description) {
        this.description = description;
    }
}
