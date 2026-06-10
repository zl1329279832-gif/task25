package com.procurement.module.delivery.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DeliveryVO {
    private Long id;
    private String deliveryNo;
    private Long orderId;
    private String orderNo;
    private Long supplierId;
    private String status;
    private LocalDate deliveryDate;
    private Long receiverId;
    private LocalDateTime receiveTime;
    private String remark;
    private LocalDateTime createTime;
    private List<DeliveryItemVO> items;
    private List<DeliveryDiffVO> diffs;

    @Data
    public static class DeliveryItemVO {
        private Long id;
        private Long orderItemId;
        private Long materialId;
        private String materialName;
        private BigDecimal expectedQuantity;
        private BigDecimal actualQuantity;
    }

    @Data
    public static class DeliveryDiffVO {
        private Long id;
        private Long materialId;
        private String materialName;
        private String diffType;
        private BigDecimal diffQuantity;
        private String description;
        private LocalDateTime createTime;
    }
}
