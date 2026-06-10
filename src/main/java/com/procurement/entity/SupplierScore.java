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
    private Integer ruleVersion;
    private BigDecimal totalScore;
    private BigDecimal quoteResponseScore;
    private BigDecimal priceDeviationScore;
    private BigDecimal deliveryOnTimeScore;
    private BigDecimal arrivalDiffScore;
    private BigDecimal qcRejectScore;
    private BigDecimal returnRateScore;
    private BigDecimal invoiceDiffScore;
    private BigDecimal approvalAnomalyScore;
    private String scoreLevel;
    private LocalDateTime calculatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
