package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier_score_snapshot")
public class SupplierScoreSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long poId;
    private Long quoteId;
    private Long supplierId;
    private BigDecimal totalScore;
    private Integer ruleVersionNo;
    private String snapshotData;
    private LocalDateTime createdAt;
}
