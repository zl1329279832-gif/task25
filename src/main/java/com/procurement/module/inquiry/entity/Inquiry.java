package com.procurement.module.inquiry.entity;

import com.procurement.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inquiry")
public class Inquiry extends BaseEntity {
    private String inquiryNo;
    private String title;
    private String status;
    private LocalDateTime publishTime;
    private LocalDateTime deadline;
    private LocalDateTime closeTime;
    private Long buyerId;
    private String remark;
}
