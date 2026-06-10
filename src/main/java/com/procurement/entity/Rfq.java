package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rfq")
public class Rfq {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String rfqNo;
    private String title;
    private Long purchaserId;
    private LocalDateTime deadline;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
