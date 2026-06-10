package com.procurement.common.enums;

import lombok.Getter;

@Getter
public enum DeliveryStatus {
    PENDING("待检验"),
    INSPECTING("检验中"),
    ACCEPTED("已接收"),
    REJECTED("已拒收"),
    PARTIAL_ACCEPTED("部分接收");

    private final String description;

    DeliveryStatus(String description) {
        this.description = description;
    }
}
