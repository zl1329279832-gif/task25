package com.procurement.module.returns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ReturnCreateDTO {
    @NotNull
    private Long orderId;
    @NotNull
    private Long supplierId;
    private Long deliveryId;
    private Long inspectionId;
    @NotNull
    private String reason;
    @NotEmpty
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private Long materialId;
        @NotNull
        private BigDecimal returnQuantity;
        @NotNull
        private BigDecimal unitPrice;
        private String reason;
    }
}
