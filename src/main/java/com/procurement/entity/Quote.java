package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("quote")
public class Quote {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String quoteNo;
    private Long rfqId;
    private Long supplierId;
    private Integer version;
    private BigDecimal totalAmount;
    private String status;
    private Integer frozen;
    private BigDecimal scoreAtFreeze;
    private Integer scoreRuleVersionAtFreeze;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
