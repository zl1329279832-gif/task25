package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("quote_line")
public class QuoteLine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long quoteId;
    private Long rfqLineId;
    private Long materialId;
    private BigDecimal unitPrice;
    private BigDecimal quantity;
    private Integer deliveryDays;
    private String remark;
}
