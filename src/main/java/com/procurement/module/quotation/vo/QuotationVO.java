package com.procurement.module.quotation.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuotationVO {
    private Long id;
    private String quotationNo;
    private Long inquiryId;
    private Long supplierId;
    private String supplierName;
    private Integer version;
    private String status;
    private BigDecimal totalAmount;
    private LocalDate validUntil;
    private LocalDateTime submitTime;
    private LocalDateTime frozenTime;
    private String remark;
    private LocalDateTime createTime;
    private List<QuotationItemVO> items;

    @Data
    public static class QuotationItemVO {
        private Long id;
        private Long inquiryItemId;
        private Long materialId;
        private String materialName;
        private BigDecimal unitPrice;
        private BigDecimal quantity;
        private BigDecimal amount;
        private Integer deliveryDays;
        private String remark;
    }
}
