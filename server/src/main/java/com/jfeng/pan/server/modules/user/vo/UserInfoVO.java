package com.jfeng.pan.server.modules.user.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jfeng.pan.web.serializer.IdEncryptSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Tag(name="用户基本信息响应对象", description = "用户后端向前端返回实体信息")
public class UserInfoVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 3457810454654L;

    @Schema(description = "用户名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "用户根目录的加密ID")
    @JsonSerialize(using = IdEncryptSerializer.class)
    private Long rootFiled;

    @Schema(description = "用户根目录名称")
    private String rootFilename;


}
