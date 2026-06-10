package com.procurement.module.invoice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.procurement.module.invoice.entity.Invoice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InvoiceMapper extends BaseMapper<Invoice> {
}
