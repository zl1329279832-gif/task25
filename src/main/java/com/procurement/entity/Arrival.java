package com.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("arrival")
public class Arrival {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String arrivalNo;
    private Long poId;
    private Integer batchNo;
    private String status;
    private LocalDateTime arrivedAt;
    private Long receiverId;
    private String remark;
    private LocalDateTime createdAt;
}
