package com.procurement.module.material.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MaterialCreateDTO {
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;
    @NotBlank(message = "物料名称不能为空")
    private String materialName;
    private String specification;
    @NotBlank(message = "计量单位不能为空")
    private String unit;
    private BigDecimal referencePrice;
    private String description;
}
