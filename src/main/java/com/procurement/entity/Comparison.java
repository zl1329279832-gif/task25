package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("comparison")
public class Comparison {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String comparisonNo;
    private Long rfqId;
    private String rule;
    private String resultSummary;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
