package com.jfeng.pan.core.utils;

/**
 * 密码工具类
 * Created by RubinChu on 2021/1/22 下午 4:11
 */
public class PasswordUtil {

    /**
     * 随机生成盐值
     *
     * @return
     */
    public static String getSalt() {
        return MessageDigestUtil.md5(UUIDUtil.getUUID());
    }

    /**
     * 密码加密函数
     *
     * @param salt 加密盐值
     * @param inputPassword 加密密码
     * @return 密码加密后的字符串
     */
    public static String encryptPassword(String salt, String inputPassword) {
        return MessageDigestUtil.sha256(MessageDigestUtil.sha1(inputPassword) + salt);
    }

}
