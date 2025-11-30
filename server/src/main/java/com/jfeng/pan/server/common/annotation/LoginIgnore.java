package com.jfeng.pan.server.common.annotation;

import java.lang.annotation.*;

/**
 *  该注解影响那些不需要登录的接口
 *  标注该主机的方法会自动屏蔽统一的登录拦截校验器
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LoginIgnore {
}
