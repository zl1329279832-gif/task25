package com.procurement.module.invoice.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InvoiceVO {
    private Long id;
    private String invoiceNo;
    private String invoiceCode;
    private Long orderId;
    private Long supplierId;
    private String status;
    private String invoiceType;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private LocalDate invoiceDate;
    private Long registrarId;
    private LocalDateTime verifyTime;
    private String rejectReason;
    private LocalDateTime createTime;
    private List<InvoiceItemVO> items;

    @Data
    public static class InvoiceItemVO {
        private Long id;
        private Long orderItemId;
        private Long materialId;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private BigDecimal taxRate;
    }
}
