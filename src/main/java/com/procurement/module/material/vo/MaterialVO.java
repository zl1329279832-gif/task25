package com.procurement.module.material.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MaterialVO {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;
    private BigDecimal referencePrice;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
}
