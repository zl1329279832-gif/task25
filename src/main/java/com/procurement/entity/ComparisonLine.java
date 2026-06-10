package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("comparison_line")
public class ComparisonLine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long comparisonId;
    private Long quoteId;
    private Long supplierId;
    private BigDecimal totalAmount;
    private Integer avgDelivery;
    private BigDecimal score;
    private Integer rankNo;
    private Integer selected;
}
