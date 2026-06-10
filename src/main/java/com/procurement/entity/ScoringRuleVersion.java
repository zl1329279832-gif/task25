package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scoring_rule_version")
public class ScoringRuleVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer versionNo;
    private String weights;
    private String thresholds;
    private LocalDateTime effectiveAt;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
