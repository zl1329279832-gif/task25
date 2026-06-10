package com.procurement.module.comparison.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("comparison_item")
public class ComparisonItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long comparisonId;
    private Long quotationId;
    private Long supplierId;
    private Long materialId;
    private BigDecimal unitPrice;
    private BigDecimal score;
    private Integer priceRank;
    private Integer isSelected;
    private String remark;
}
