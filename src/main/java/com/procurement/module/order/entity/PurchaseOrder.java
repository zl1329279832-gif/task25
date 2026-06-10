package com.procurement.module.order.entity;

import com.procurement.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_order")
public class PurchaseOrder extends BaseEntity {
    private String orderNo;
    private Long comparisonId;
    private Long supplierId;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private Long buyerId;
    private LocalDate expectedDeliveryDate;
    private LocalDate actualDeliveryDate;
    private LocalDateTime confirmTime;
    private String approvalThreshold;
    private String remark;
}
