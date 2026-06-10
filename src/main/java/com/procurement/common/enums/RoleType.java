package com.procurement.common.enums;

import lombok.Getter;

@Getter
public enum RoleType {
    BUYER("BUYER", "采购员"),
    PURCHASE_MANAGER("PURCHASE_MANAGER", "采购主管"),
    SUPPLIER("SUPPLIER", "供应商"),
    WAREHOUSE("WAREHOUSE", "仓库人员"),
    FINANCE("FINANCE", "财务");

    private final String code;
    private final String name;

    RoleType(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
