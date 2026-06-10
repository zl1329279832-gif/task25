package com.procurement.module.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("delivery_item")
public class DeliveryItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deliveryId;
    private Long orderItemId;
    private Long materialId;
    private BigDecimal expectedQuantity;
    private BigDecimal actualQuantity;
    private String remark;
}
