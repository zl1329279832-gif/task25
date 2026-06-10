package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("reconciliation_line")
public class ReconciliationLine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reconId;
    private Long materialId;
    private Long poLineId;
    private BigDecimal orderedQty;
    private BigDecimal receivedQty;
    private BigDecimal invoicedQty;
    private BigDecimal unitPrice;
    private BigDecimal orderAmount;
    private BigDecimal receiptAmount;
    private BigDecimal invoiceAmount;
    private BigDecimal diffAmount;
}
