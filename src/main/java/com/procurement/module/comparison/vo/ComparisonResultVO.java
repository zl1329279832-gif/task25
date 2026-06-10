package com.procurement.module.comparison.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ComparisonResultVO {
    private Long id;
    private String comparisonNo;
    private Long inquiryId;
    private String comparisonType;
    private String status;
    private LocalDateTime createTime;
    private List<ComparisonItemVO> items;

    @Data
    public static class ComparisonItemVO {
        private Long id;
        private Long quotationId;
        private Long supplierId;
        private String supplierName;
        private Long materialId;
        private String materialName;
        private BigDecimal unitPrice;
        private BigDecimal score;
        private Integer priceRank;
        private Boolean selected;
    }
}
