package com.procurement.audit;

import java.lang.annotation.*;

/**
 * 操作审计注解 - 标注在 Service 方法上自动记录审计日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    String action();
    String entityType() default "";
}
