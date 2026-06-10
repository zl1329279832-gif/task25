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
    private Long supplierId;
    private String businessType;
    private Long businessId;
    private Integer ruleVersion;
    private BigDecimal totalScore;
    private String scoreLevel;
    private String scoreDetail;
    private LocalDateTime snapshotAt;
}
