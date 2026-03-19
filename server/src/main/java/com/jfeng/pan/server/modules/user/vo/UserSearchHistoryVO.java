package com.jfeng.pan.server.modules.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Tag(name="用户搜索历史返回实体", description = "用户后端向前端返回实体信息")
public class UserSearchHistoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = -128364124845L;

    @Schema(name = "搜索文案")
    private String value;
}
