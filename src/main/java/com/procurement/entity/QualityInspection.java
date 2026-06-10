package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quality_inspection")
public class QualityInspection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String inspectionNo;
    private Long arrivalId;
    private Long inspectorId;
    private String result;
    private String remark;
    private LocalDateTime inspectedAt;
}
