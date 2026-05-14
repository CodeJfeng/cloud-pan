package com.jfeng.pan.server.common.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Http工具类
 * Created by RubinChu on 2021/1/19 17:54
 */
public class HttpUtil {

    /**
     * 获取客户端真实IP地址
     *
     * @param request HTTP请求
     * @return 客户端IP地址
     */
    public static String getIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index);
            } else {
                return ip;
            }
        }
        
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        
        ip = request.getHeader("Proxy-Client-IP");
        if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * 添加跨域的响应头
     *
     * @param response
     */
    public static void addCorsResponseHeaders(HttpServletResponse response) {
        for (CorsConfigEnum corsConfigEnum : CorsConfigEnum.values()) {
            response.setHeader(corsConfigEnum.getKey(), corsConfigEnum.getValue());
        }
    }

    /**
     * 跨域设置枚举类
     */
    @AllArgsConstructor
    @Getter
    public enum CorsConfigEnum {
        /**
         * 允许所有远程访问
         */
        CORS_ORIGIN("Access-Control-Allow-Origin", "*"),
        /**
         * 允许认证
         */
        CORS_CREDENTIALS("Access-Control-Allow-Credentials", "true"),
        /**
         * 允许远程调用的请求类型
         */
        CORS_METHODS("Access-Control-Allow-Methods", "POST, GET, PATCH, DELETE, PUT"),
        /**
         * 指定本次预检请求的有效期，单位是秒
         */
        CORS_MAX_AGE("Access-Control-Max-Age", "3600"),
        /**
         * 允许所有请求头
         */
        CORS_HEADERS("Access-Control-Allow-Headers", "*");

        private String key;
        private String value;

    }

}
