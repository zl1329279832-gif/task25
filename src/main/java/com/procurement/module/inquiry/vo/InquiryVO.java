package com.procurement.module.inquiry.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InquiryVO {
    private Long id;
    private String inquiryNo;
    private String title;
    private String status;
    private LocalDateTime publishTime;
    private LocalDateTime deadline;
    private LocalDateTime closeTime;
    private Long buyerId;
    private String buyerName;
    private String remark;
    private LocalDateTime createTime;
    private List<InquiryItemVO> items;
    private List<Long> supplierIds;

    @Data
    public static class InquiryItemVO {
        private Long id;
        private Long materialId;
        private String materialName;
        private String specification;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal expectedPrice;
        private LocalDate requiredDate;
        private String remark;
    }
}
