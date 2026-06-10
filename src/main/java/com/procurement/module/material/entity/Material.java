package com.procurement.module.material.entity;

import com.procurement.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("material")
public class Material extends BaseEntity {
    private Long categoryId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;
    private BigDecimal referencePrice;
    private String description;
    private Integer status;
}
