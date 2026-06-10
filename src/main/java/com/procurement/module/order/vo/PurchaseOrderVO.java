package com.procurement.module.order.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseOrderVO {
    private Long id;
    private String orderNo;
    private Long comparisonId;
    private Long supplierId;
    private String supplierName;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private Long buyerId;
    private String buyerName;
    private LocalDate expectedDeliveryDate;
    private LocalDate actualDeliveryDate;
    private LocalDateTime confirmTime;
    private String approvalThreshold;
    private String remark;
    private LocalDateTime createTime;
    private List<OrderItemVO> items;

    @Data
    public static class OrderItemVO {
        private Long id;
        private Long materialId;
        private String materialName;
        private BigDecimal unitPrice;
        private BigDecimal quantity;
        private BigDecimal receivedQuantity;
        private BigDecimal amount;
    }
}
