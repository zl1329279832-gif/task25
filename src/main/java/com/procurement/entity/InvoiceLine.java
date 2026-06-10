package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("invoice_line")
public class InvoiceLine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long invoiceId;
    private Long poLineId;
    private Long materialId;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
