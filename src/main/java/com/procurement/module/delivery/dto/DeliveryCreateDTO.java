package com.procurement.module.delivery.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class DeliveryCreateDTO {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
    @NotNull(message = "到货日期不能为空")
    private LocalDate deliveryDate;
    private String remark;
    @NotEmpty(message = "到货明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        private Long orderItemId;
        private Long materialId;
        private BigDecimal expectedQuantity;
        private BigDecimal actualQuantity;
        private String remark;
    }
}
