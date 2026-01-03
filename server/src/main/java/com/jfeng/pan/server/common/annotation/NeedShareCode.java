package com.jfeng.pan.server.common.annotation;

import java.lang.annotation.*;

/**
 *  该注解主要影响需要分享码校验的接口
 *  标注该主机的方法会通过AOP去实现校验的TOKEN对shareID的线程注入
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NeedShareCode {
}
