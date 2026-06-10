package com.procurement.module.quotation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class QuotationSubmitDTO {
    @NotNull(message = "询价单ID不能为空")
    private Long inquiryId;
    private LocalDate validUntil;
    private String remark;
    @NotEmpty(message = "报价明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        private Long inquiryItemId;
        private Long materialId;
        private BigDecimal unitPrice;
        private BigDecimal quantity;
        private Integer deliveryDays;
        private String remark;
    }
}
