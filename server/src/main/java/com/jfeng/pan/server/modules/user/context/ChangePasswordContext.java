package com.jfeng.pan.server.modules.user.context;

import com.jfeng.pan.server.modules.user.entity.RPanUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户在线修改密码上下文实体
 */
@Data
public class ChangePasswordContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -563735403755L;

    /**
     * 当前用户登录id
     */
    private Long userId;

    /**
     * 用户旧密码
     */
    private String oldPassword;

    /**
     * 用户新密码
     */
    private String newPassword;

    /**
     * 当前登录用户的实体信息
     */
    private RPanUser entity;
}
