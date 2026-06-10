package com.procurement.module.supplier.entity;

import com.procurement.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("supplier")
public class Supplier extends BaseEntity {
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
}
