package com.jfeng.pan.server.modules.user.context;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 *     <li>用户忘记密码业务的上下文对象</li>
 *     <li>本实体与PO对象无区别，当业务更改时，更好的维护项目</li>
 * </p>
 */
@Data
@Tag(name = "用户忘记密码-校验用户名参数")
public class CheckUsernameContext implements Serializable {
    @Serial
    private static final long serialVersionUID = 23812371985L;

    private String username;

}
