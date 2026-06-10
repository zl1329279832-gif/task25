package com.procurement.module.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class InvoiceCreateDTO {
    @NotNull
    private String invoiceNo;
    private String invoiceCode;
    @NotNull
    private Long orderId;
    @NotNull
    private Long supplierId;
    @NotNull
    private String invoiceType; // NORMAL / SPECIAL
    @NotNull
    private BigDecimal amount;
    @NotNull
    private BigDecimal taxAmount;
    @NotNull
    private BigDecimal totalAmount;
    @NotNull
    private LocalDate invoiceDate;
    @NotEmpty
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        private Long orderItemId;
        @NotNull
        private Long materialId;
        @NotNull
        private BigDecimal quantity;
        @NotNull
        private BigDecimal unitPrice;
        @NotNull
        private BigDecimal amount;
        private BigDecimal taxRate;
    }
}
