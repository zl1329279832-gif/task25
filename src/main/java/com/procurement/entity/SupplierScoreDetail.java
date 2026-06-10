package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier_score_detail")
public class SupplierScoreDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private Integer ruleVersion;
    private String dimension;
    private BigDecimal rawValue;
    private BigDecimal score;
    private BigDecimal weightedScore;
    private String dataSummary;
    private LocalDateTime calculatedAt;
}
