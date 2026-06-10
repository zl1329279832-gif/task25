package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("arrival_line")
public class ArrivalLine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long arrivalId;
    private Long poLineId;
    private Long materialId;
    private BigDecimal orderedQty;
    private BigDecimal arrivedQty;
    private BigDecimal acceptedQty;
    private BigDecimal diffQty;
    private String diffRemark;
}
