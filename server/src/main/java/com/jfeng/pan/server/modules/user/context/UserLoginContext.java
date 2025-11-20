package com.jfeng.pan.server.modules.user.context;

import com.jfeng.pan.server.modules.user.entity.RPanUser;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 *     用户注册业务的上下文对象
 *     业务处理中，存储所有的上下文对象
 *     本实体与PO对象无区别，当业务更改时，更好的维护项目
 * </p>
 */
@Data
public class UserLoginContext implements Serializable {

    @Serial
    private static final long  serialVersionUID = -45624137318L;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 用户实体对象
     */
    private RPanUser entity;

    /**
     * 登录成功的accessToken
     */
    private String accessToken;

}
