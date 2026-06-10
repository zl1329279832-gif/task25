package com.procurement.common.aspect;

import com.procurement.common.annotation.RequireRole;
import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.security.SecurityUser;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class PermissionAspect {

    @Before("@annotation(requireRole)")
    public void checkRole(JoinPoint point, RequireRole requireRole) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        boolean hasRole = Arrays.stream(requireRole.value())
                .anyMatch(role -> auth.getAuthorities().contains(
                        new SimpleGrantedAuthority("ROLE_" + role)));

        if (!hasRole) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
