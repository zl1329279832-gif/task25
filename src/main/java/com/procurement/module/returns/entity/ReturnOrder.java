package com.procurement.module.returns.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("return_order")
public class ReturnOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String returnNo;
    private Long orderId;
    private Long supplierId;
    private Long deliveryId;
    private Long inspectionId;
    private String status;
    private String reason;
    private BigDecimal totalAmount;
    private Long applicantId;
    private LocalDateTime supplierConfirmTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
