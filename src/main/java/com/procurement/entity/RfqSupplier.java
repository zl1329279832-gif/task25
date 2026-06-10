package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rfq_supplier")
public class RfqSupplier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rfqId;
    private Long supplierId;
    private LocalDateTime invitedAt;
}
