package com.procurement.module.supplier.dto;

import lombok.Data;

@Data
public class SupplierUpdateDTO {
    private String supplierName;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private String bankName;
    private String bankAccount;
    private String remark;
}
