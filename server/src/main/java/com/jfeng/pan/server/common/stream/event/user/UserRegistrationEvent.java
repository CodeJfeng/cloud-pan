package com.jfeng.pan.server.common.stream.event.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户注册事件实体
 * 用于在用户注册成功后，发送消息到MQ通知其他系统完成用户注册
 * 消息发送到 user-registration_topic
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = -987654321012L;

    /**
     * 业务唯一标识键
     * 格式：user-registration-{userId}
     */
    private String keys;

    /**
     * 用户注册消息体
     */
    private UserRegistrationBody body;

    /**
     * 用户注册消息体
     * 包含用户的基本注册信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRegistrationBody implements Serializable {

        @Serial
        private static final long serialVersionUID = -987654321013L;

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 用户名
         */
        private String username;

        /**
         * 密码（明文）
         */
        private String password;
    }
}
