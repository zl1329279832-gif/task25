package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier_score_rule")
public class ScoreRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer version;
    private String name;
    private BigDecimal quoteResponseWeight;
    private BigDecimal priceDeviationWeight;
    private BigDecimal deliveryOnTimeWeight;
    private BigDecimal arrivalDiffWeight;
    private BigDecimal qcRejectWeight;
    private BigDecimal returnRateWeight;
    private BigDecimal invoiceDiffWeight;
    private BigDecimal approvalAnomalyWeight;
    private BigDecimal warningThreshold;
    private BigDecimal blockThreshold;
    private Integer active;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
