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
 * 校验校验密保答案PO对象
 */
@Data
@Tag(name = "用户忘记密码-校验密保答案")
public class CheckAnswerPO implements Serializable {
    @Serial
    private static final long serialVersionUID = 4657562342L;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[0-9A-Za-z]{6,16}$", message = "请输入6-16位只包含数字和字母的用户名")
    private String username;

    @Schema(description = "密保问题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密保问题不能为空")
    @Length(max = 100, message = "密保问题不能超过100个字符")
    private String question;

    @Schema(description = "密保答案", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密保答案不能为空")
    @Length(max = 100, message = "密保答案不能超过100个字符")
    private String answer;


}
