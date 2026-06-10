package com.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.procurement.entity.InvoiceLine;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InvoiceLineMapper extends BaseMapper<InvoiceLine> {
}
