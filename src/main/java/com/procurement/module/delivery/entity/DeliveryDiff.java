package com.procurement.module.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("delivery_diff")
public class DeliveryDiff {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deliveryId;
    private Long deliveryItemId;
    private Long materialId;
    private String diffType;
    private BigDecimal diffQuantity;
    private String description;
    private LocalDateTime createTime;
}
