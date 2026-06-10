package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier_score")
public class SupplierScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private Long ruleVersionId;
    private BigDecimal totalScore;
    private Integer sampleSize;
    private LocalDateTime calculatedAt;
    private String source;
}
