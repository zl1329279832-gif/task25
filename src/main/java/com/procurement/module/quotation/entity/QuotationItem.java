package com.procurement.module.quotation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("quotation_item")
public class QuotationItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long quotationId;
    private Long inquiryItemId;
    private Long materialId;
    private BigDecimal unitPrice;
    private BigDecimal quantity;
    private BigDecimal amount;
    private Integer deliveryDays;
    private String remark;
}
