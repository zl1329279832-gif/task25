package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("supplier_score_detail")
public class SupplierScoreDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierScoreId;
    private String dimension;
    private BigDecimal rawValue;
    private BigDecimal normalizedScore;
    private BigDecimal weight;
    private BigDecimal weightedScore;
    private String dataSummary;
}
