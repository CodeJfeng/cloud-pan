package com.jfeng.pan.lock.core.annotation;

import com.jfeng.pan.lock.core.key.KeyGenerator;
import com.jfeng.pan.lock.core.key.StandardKeyGenerator;

import java.lang.annotation.*;

/**
 * 自定义锁的注解
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Lock {

    /**
     * 锁的名称
     *
     * @return
     */
    String name() default "";

    /**
     * 锁的过期时间
     *
     * @return
     */
    long expireSecond() default 60L;

    /**
     * 自定义锁的Key，支持EL表达式
     *
     * @return
     */
    String[] keys() default {};

    /**
     * 自定义锁的Key生成器
     *
     * @return
     */
    Class<? extends KeyGenerator> KeyGenerator() default StandardKeyGenerator.class;
}
