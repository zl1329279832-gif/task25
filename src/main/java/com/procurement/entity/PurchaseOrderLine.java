package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("purchase_order_line")
public class PurchaseOrderLine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long poId;
    private Long materialId;
    private BigDecimal quantity;
    private BigDecimal receivedQty;
    private BigDecimal acceptedQty;
    private BigDecimal rejectedQty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
}
