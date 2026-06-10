package com.procurement.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.entity.AuditLog;
import com.procurement.mapper.AuditLogMapper;
import com.procurement.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
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
public class AuditAspect {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Object result = pjp.proceed();

        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(auditable.action());
            auditLog.setEntityType(auditable.entityType().isEmpty()
                    ? pjp.getTarget().getClass().getSimpleName()
                    : auditable.entityType());
            auditLog.setCreatedAt(LocalDateTime.now());

            // 从方法参数中尝试提取 entity ID
            Object[] args = pjp.getArgs();
            if (args.length > 0 && args[0] instanceof Long) {
                auditLog.setEntityId((Long) args[0]);
            }

            // 记录参数摘要
            try {
                auditLog.setDetail(objectMapper.writeValueAsString(args));
            } catch (Exception ignored) {}

            // 获取当前用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
                auditLog.setUserId(lu.getUserId());
                auditLog.setUsername(lu.getUsername());
            }

            // 获取 IP
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                auditLog.setIpAddress(req.getRemoteAddr());
            }

            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("审计日志记录失败: {}", e.getMessage());
        }

        return result;
    }
}
