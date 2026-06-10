package com.procurement.module.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("delivery")
public class Delivery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deliveryNo;
    private Long orderId;
    private Long supplierId;
    private String status;
    private LocalDate deliveryDate;
    private Long receiverId;
    private LocalDateTime receiveTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
