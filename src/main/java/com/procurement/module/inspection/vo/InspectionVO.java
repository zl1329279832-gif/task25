package com.procurement.module.inspection.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InspectionVO {
    private Long id;
    private String inspectionNo;
    private Long deliveryId;
    private Long deliveryItemId;
    private Long materialId;
    private BigDecimal inspectQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal unqualifiedQuantity;
    private String result;
    private Long inspectorId;
    private LocalDateTime inspectTime;
    private String remark;
    private LocalDateTime createTime;
}
