package com.procurement.module.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inspection")
public class Inspection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String inspectionNo;
    private Long deliveryId;
    private Long deliveryItemId;
    private Long materialId;
    private BigDecimal inspectQuantity;
    private BigDecimal qualifiedQuantity;
    private BigDecimal unqualifiedQuantity;
    private String result;
    private Long inspectorId;
    private LocalDateTime inspectTime;
    private String remark;
    private LocalDateTime createTime;
}
