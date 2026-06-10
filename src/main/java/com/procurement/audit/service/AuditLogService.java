package com.procurement.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.audit.entity.AuditLogEntity;
import com.procurement.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public void log(String module, String operation, String businessType, Long businessId,
                    String detail, Long userId, String username) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setModule(module);
        entity.setOperation(operation);
        entity.setBusinessType(businessType);
        entity.setBusinessId(businessId);
        entity.setDetail(detail);
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(entity);
    }

    public Page<AuditLogEntity> page(int pageNum, int pageSize, String module) {
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (module != null && !module.isEmpty()) {
            wrapper.eq(AuditLogEntity::getModule, module);
        }
        wrapper.orderByDesc(AuditLogEntity::getCreateTime);
        return auditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
