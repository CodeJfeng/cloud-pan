package com.jfeng.pan.server.common.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解
 * 基于 Redis + Lua 实现分布式限流
 * 标注该注解的方法会在指定时间窗口内限制访问次数
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {

  /**
   * 限流键前缀
   * 默认为空，使用类名+方法名作为键
   */
  String key() default "";

  /**
   * 时间窗口内允许的最大请求数
   * 默认 10 次
   */
  int permits() default 10;

  /**
   * 时间窗口大小（秒）
   * 默认 60 秒
   */
  int seconds() default 60;

  /**
   * 限流提示信息
   */
  String message() default "请求过于频繁，请稍后重试";

  /**
   * 限流维度
   * 默认按用户限流（需要登录），可选 IP 或全局
   */
  LimitType limitType() default LimitType.USER;

  /**
   * 限流维度枚举
   */
  enum LimitType {
    /**
     * 按用户限流
     */
    USER,

    /**
     * 按 IP 限流
     */
    IP,

    /**
     * 全局限流（所有请求共享配额）
     */
    GLOBAL
  }
}
