package com.procurement.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.procurement.module.order.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
}
