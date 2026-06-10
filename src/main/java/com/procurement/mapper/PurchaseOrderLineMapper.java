package com.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.procurement.entity.PurchaseOrderLine;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PurchaseOrderLineMapper extends BaseMapper<PurchaseOrderLine> {
}
