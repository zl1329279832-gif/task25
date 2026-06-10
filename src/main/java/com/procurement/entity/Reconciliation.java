package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("reconciliation")
public class Reconciliation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reconNo;
    private Long poId;
    private Long supplierId;
    private BigDecimal orderAmount;
    private BigDecimal receiptAmount;
    private BigDecimal invoiceAmount;
    private BigDecimal diffAmount;
    private String status;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
