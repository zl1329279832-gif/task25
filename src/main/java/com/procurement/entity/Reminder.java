package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reminder")
public class Reminder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String businessType;
    private Long businessId;
    private Long targetUserId;
    private String message;
    private String status;
    private LocalDateTime triggerTime;
    private LocalDateTime createdAt;
}
