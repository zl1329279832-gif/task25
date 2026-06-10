package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rfq_line")
public class RfqLine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rfqId;
    private Long materialId;
    private BigDecimal quantity;
    private String description;
    private LocalDateTime createdAt;
}
