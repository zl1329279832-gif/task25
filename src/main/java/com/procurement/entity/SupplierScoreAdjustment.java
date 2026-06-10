package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier_score_adjustment")
public class SupplierScoreAdjustment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private BigDecimal oldScore;
    private BigDecimal newScore;
    private String reason;
    private Long adjustedBy;
    private LocalDateTime createdAt;
}
