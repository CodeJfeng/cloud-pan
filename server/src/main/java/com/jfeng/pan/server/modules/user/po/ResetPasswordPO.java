package com.jfeng.pan.server.modules.user.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serial;
import java.io.Serializable;

/**
 * 重置用户密码PO对象
 */
@Data
@Tag(name = "用户忘记密码-重置用户密码参数")
public class ResetPasswordPO implements Serializable {
    @Serial
    private static final long serialVersionUID = 947321542342L;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[0-9A-Za-z]{6,16}$", message = "请输入6-16位只包含数字和字母的用户名")
    private String username;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    @Length(min = 8, max = 16, message = "请输入8-16位的密码")
    private String password;

    @Schema(description = "提交重置密码的token", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "token信息不能为空")
    private String token;
}
