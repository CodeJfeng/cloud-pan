package com.jfeng.pan.server.modules.user.context;

import com.jfeng.pan.server.modules.user.entity.RPanUser;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 *     <li>用户注册业务的上下文对象</li>
 *     <li>业务处理中，存储所有的上下文对象</li>
 *     <li>本实体与PO对象无区别，当业务更改时，更好的维护项目</li>
 * </p>
 */
@Data
public class UserRegisterContext implements Serializable {

    @Serial
    private static final long  serialVersionUID = -123123123121L;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 密保问题
     */
    private String question;

    /**
     * 密保答案
     */
    private String answer;

    /**
     * 用户实体对象
     */
    private RPanUser entity;

}
