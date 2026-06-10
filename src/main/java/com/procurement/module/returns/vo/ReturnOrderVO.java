package com.procurement.module.returns.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReturnOrderVO {
    private Long id;
    private String returnNo;
    private Long orderId;
    private Long supplierId;
    private Long deliveryId;
    private Long inspectionId;
    private String status;
    private String reason;
    private BigDecimal totalAmount;
    private Long applicantId;
    private LocalDateTime supplierConfirmTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private List<ReturnItemVO> items;

    @Data
    public static class ReturnItemVO {
        private Long id;
        private Long materialId;
        private BigDecimal returnQuantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private String reason;
    }
}
