package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval")
public class Approval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessType;
    private Long businessId;
    private Integer step;
    private Long approverId;
    private String status;
    private String comment;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
}
