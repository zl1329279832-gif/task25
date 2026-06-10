package com.procurement.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.audit.entity.AuditLogEntity;
import com.procurement.audit.mapper.AuditLogMapper;
import com.procurement.common.annotation.AuditLog;
import com.procurement.security.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint point, AuditLog auditLog) throws Throwable {
        Object result = point.proceed();
        try {
            saveLog(point, auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
        return result;
    }

    private void saveLog(ProceedingJoinPoint point, AuditLog auditLog) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setModule(auditLog.module());
        entity.setOperation(auditLog.operation());
        entity.setCreateTime(LocalDateTime.now());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SecurityUser user) {
            entity.setUserId(user.getUserId());
            entity.setUsername(user.getUsername());
        }

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            entity.setIpAddress(request.getRemoteAddr());
        }

        try {
            Object[] args = point.getArgs();
            if (args.length > 0) {
                entity.setDetail(objectMapper.writeValueAsString(args[0]));
            }
        } catch (Exception ignored) {
        }

        auditLogMapper.insert(entity);
    }
}
