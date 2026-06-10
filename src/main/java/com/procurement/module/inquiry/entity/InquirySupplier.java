package com.procurement.module.inquiry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("inquiry_supplier")
public class InquirySupplier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inquiryId;
    private Long supplierId;
}
