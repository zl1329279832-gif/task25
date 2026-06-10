package com.procurement.module.supplier.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SupplierVO {
    private Long id;
    private String supplierCode;
    private String supplierName;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private String qualificationStatus;
    private LocalDate qualificationExpireDate;
    private String bankName;
    private String bankAccount;
    private BigDecimal rating;
    private String remark;
    private LocalDateTime createTime;
}
