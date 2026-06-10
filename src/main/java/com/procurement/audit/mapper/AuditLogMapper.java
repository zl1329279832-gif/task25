package com.procurement.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.procurement.audit.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
