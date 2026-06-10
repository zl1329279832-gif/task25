package com.procurement.module.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("reconciliation_item")
public class ReconciliationItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reconciliationId;
    private Long orderId;
    private String orderNo;
    private BigDecimal orderAmount;
    private BigDecimal deliveredAmount;
    private BigDecimal invoicedAmount;
    private BigDecimal returnAmount;
    private BigDecimal diffAmount;
    private String diffType;
    private String remark;
}
