package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier_admission_log")
public class SupplierAdmissionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private String checkpoint;
    private String decision;
    private BigDecimal scoreAtTime;
    private Integer ruleVersionNo;
    private String reason;
    private Long operatorId;
    private Long businessId;
    private String thresholdSnapshot;
    private LocalDateTime createdAt;
}
