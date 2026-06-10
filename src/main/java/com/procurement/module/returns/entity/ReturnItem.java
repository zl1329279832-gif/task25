package com.procurement.module.returns.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("return_item")
public class ReturnItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long returnId;
    private Long materialId;
    private BigDecimal returnQuantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String reason;
}
