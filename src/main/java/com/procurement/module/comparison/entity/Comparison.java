package com.procurement.module.comparison.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("comparison")
public class Comparison {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String comparisonNo;
    private Long inquiryId;
    private String comparisonType;
    private String status;
    private String resultSummary;
    private Long operatorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
