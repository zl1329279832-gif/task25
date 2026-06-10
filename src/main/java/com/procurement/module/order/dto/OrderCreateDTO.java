package com.procurement.module.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class OrderCreateDTO {
    @NotNull(message = "比价单ID不能为空")
    private Long comparisonId;
    private LocalDate expectedDeliveryDate;
    private String remark;
}
