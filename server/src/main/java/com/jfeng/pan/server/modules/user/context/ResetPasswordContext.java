package com.jfeng.pan.server.modules.user.context;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 *     <li>用户忘记密码业务--重新设置密码的上下文对象</li>
 *     <li>本实体与PO对象无区别，当业务更改时，更好的维护项目</li>
 * </p>
 */
@Data
public class ResetPasswordContext implements Serializable {
    @Serial
    private static final long serialVersionUID = 4763347562342L;
    /**
     * 用户名称
     */
    private String username;

    /**
     * 新的密码
     */
    private String password;


    /**
     * 重置密码的token信息
     */
    private String token;


}
