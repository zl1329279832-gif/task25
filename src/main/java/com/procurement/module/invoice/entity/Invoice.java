package com.procurement.module.invoice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("invoice")
public class Invoice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String invoiceNo;
    private String invoiceCode;
    private Long orderId;
    private Long supplierId;
    private String status;
    private String invoiceType;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private LocalDate invoiceDate;
    private Long registrarId;
    private LocalDateTime verifyTime;
    private String rejectReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
