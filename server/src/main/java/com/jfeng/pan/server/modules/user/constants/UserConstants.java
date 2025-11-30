package com.jfeng.pan.server.modules.user.constants;

/**
 * 用户模块的常量类
 */
public interface UserConstants {
    /**
     * 登录用户的用户ID的key值
     */
    String LOGIN_USER_ID = "LOGIN_USER_ID";

    /**
     * 一天的毫秒时间
     */
    Long ONE_DAY_LONG = 24 * 60 * 60 * 1000L;

    /**
     * 用户登录缓存前缀
     */
    String USER_LOGIN_PREFIX = "USER_LOGIN_";

    /**
     * 用户忘记密码--重置密码临时token
     */
    String FORGET_USERNAME = "FORGET_USERNAME_";

    /**
     * 五分钟的毫秒时间
     */
    Long FIVE_MINUTES_LONG = 5 * 60 * 1000L;
}
