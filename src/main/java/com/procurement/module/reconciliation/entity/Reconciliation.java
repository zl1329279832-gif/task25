package com.procurement.module.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reconciliation")
public class Reconciliation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reconciliationNo;
    private Long supplierId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private BigDecimal orderAmount;
    private BigDecimal deliveryAmount;
    private BigDecimal invoiceAmount;
    private BigDecimal returnAmount;
    private BigDecimal netAmount;
    private Integer diffFlag;
    private String diffDescription;
    private LocalDateTime confirmTime;
    private Long operatorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
