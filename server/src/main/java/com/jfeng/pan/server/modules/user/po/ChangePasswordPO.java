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
 * 用户在线修改密码参数
 */
@Data
@Tag(name = "用户在线修改密码参数", description = "")
public class ChangePasswordPO implements Serializable {

    @Serial
    private static final long serialVersionUID = -563235403755L;


    @Schema(description = "旧密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "旧密码不能为空")
    @Length(min = 8, max = 16, message = "请输入8-16位的密码")
    private String oldPassword;

    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Length(min = 8, max = 16, message = "请输入8-16位的密码")
    private String newPassword;
}
