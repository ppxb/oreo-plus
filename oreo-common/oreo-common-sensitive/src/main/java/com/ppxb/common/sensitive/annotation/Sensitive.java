package com.ppxb.common.sensitive.annotation;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ppxb.common.sensitive.core.SensitiveStrategy;
import com.ppxb.common.sensitive.handler.SensitiveHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据脱敏注解
 *
 * @author zhujie
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveHandler.class)
public @interface Sensitive {
    SensitiveStrategy strategy();

    /**
     * 角色标识符 多个角色满足一个即可
     */
    String[] roleKey() default {};

    /**
     * 权限标识符 多个权限满足一个即可
     */
    String[] perms() default {};
}
