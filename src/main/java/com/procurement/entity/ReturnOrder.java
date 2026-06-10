package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("return_order")
public class ReturnOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String returnNo;
    private Long poId;
    private Long arrivalId;
    private Long supplierId;
    private String reason;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
}
