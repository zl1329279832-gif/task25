package com.procurement.module.material.entity;

import com.procurement.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("material_category")
public class MaterialCategory extends BaseEntity {
    private Long parentId;
    private String categoryCode;
    private String categoryName;
    private Integer sortOrder;
}
