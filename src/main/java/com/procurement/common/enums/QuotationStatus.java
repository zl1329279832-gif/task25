package com.procurement.common.enums;

import lombok.Getter;

@Getter
public enum QuotationStatus {
    DRAFT("草稿"),
    SUBMITTED("已提交"),
    FROZEN("已冻结"),
    SELECTED("已选中"),
    REJECTED("未选中");

    private final String description;

    QuotationStatus(String description) {
        this.description = description;
    }
}
