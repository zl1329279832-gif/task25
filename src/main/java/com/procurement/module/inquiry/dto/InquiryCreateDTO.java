package com.procurement.module.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InquiryCreateDTO {
    @NotBlank(message = "询价标题不能为空")
    private String title;
    private LocalDateTime deadline;
    private String remark;
    @NotEmpty(message = "询价明细不能为空")
    private List<Item> items;
    private List<Long> supplierIds;

    @Data
    public static class Item {
        private Long materialId;
        private BigDecimal quantity;
        private BigDecimal expectedPrice;
        private LocalDate requiredDate;
        private String remark;
    }
}
