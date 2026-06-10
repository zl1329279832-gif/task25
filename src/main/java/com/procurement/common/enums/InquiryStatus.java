package com.procurement.common.enums;

import lombok.Getter;

@Getter
public enum InquiryStatus {
    DRAFT("草稿"),
    PUBLISHED("已发布"),
    QUOTING("报价中"),
    CLOSED("已关闭"),
    CANCELLED("已取消");

    private final String description;

    InquiryStatus(String description) {
        this.description = description;
    }
}
