package com.procurement.module.reconciliation.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReconciliationVO {
    private Long id;
    private String reconciliationNo;
    private Long supplierId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private BigDecimal orderAmount;
    private BigDecimal deliveryAmount;
    private BigDecimal invoiceAmount;
    private BigDecimal returnAmount;
    private BigDecimal netAmount;
    private Integer diffFlag;
    private String diffDescription;
    private LocalDateTime confirmTime;
    private Long operatorId;
    private LocalDateTime createTime;
    private List<ReconciliationItemVO> items;

    @Data
    public static class ReconciliationItemVO {
        private Long id;
        private Long orderId;
        private String orderNo;
        private BigDecimal orderAmount;
        private BigDecimal deliveredAmount;
        private BigDecimal invoicedAmount;
        private BigDecimal returnAmount;
        private BigDecimal diffAmount;
        private String diffType;
        private String remark;
    }
}
