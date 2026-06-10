package com.procurement.module.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.procurement.module.invoice.entity.InvoiceItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InvoiceItemMapper extends BaseMapper<InvoiceItem> {
}
