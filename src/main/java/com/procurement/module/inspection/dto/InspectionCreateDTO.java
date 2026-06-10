package com.procurement.module.inspection.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class InspectionCreateDTO {
    @NotNull
    private Long deliveryId;
    @NotNull
    private Long deliveryItemId;
    @NotNull
    private Long materialId;
    @NotNull
    private BigDecimal inspectQuantity;
    @NotNull
    private BigDecimal qualifiedQuantity;
    private BigDecimal unqualifiedQuantity;
    @NotNull
    private String result; // QUALIFIED / UNQUALIFIED / CONCESSION_ACCEPT
    private String remark;
}
