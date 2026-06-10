package com.procurement.module.inquiry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("inquiry_item")
public class InquiryItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inquiryId;
    private Long materialId;
    private BigDecimal quantity;
    private BigDecimal expectedPrice;
    private LocalDate requiredDate;
    private String remark;
}
