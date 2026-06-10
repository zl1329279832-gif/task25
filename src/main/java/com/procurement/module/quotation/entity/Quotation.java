package com.procurement.module.quotation.entity;

import com.procurement.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("quotation")
public class Quotation extends BaseEntity {
    private String quotationNo;
    private Long inquiryId;
    private Long supplierId;
    private Integer version;
    private String status;
    private BigDecimal totalAmount;
    private LocalDate validUntil;
    private LocalDateTime submitTime;
    private LocalDateTime frozenTime;
    private String remark;
}
