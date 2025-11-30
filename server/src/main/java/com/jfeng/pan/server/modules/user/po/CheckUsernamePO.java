package com.jfeng.pan.server.modules.user.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serial;
import java.io.Serializable;

/**
 * 校验用户名称PO对象
 */
@Data
@Tag(name = "用户忘记密码-校验用户名参数")
public class CheckUsernamePO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1231283545L;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[0-9A-Za-z]{6,16}$", message = "请输入6-16位只包含数字和字母的用户名")
    private String username;


}
