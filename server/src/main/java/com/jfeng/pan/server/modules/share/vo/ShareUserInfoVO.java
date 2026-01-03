package com.jfeng.pan.server.modules.share.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jfeng.pan.web.serializer.IdEncryptSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Tag(name = "分享者信息返回实体")
@Data
public class ShareUserInfoVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -86527349643781L;

    @Schema(name = "分享者的ID")
    @JsonSerialize(using = IdEncryptSerializer.class)
    private Long userId;

    @Schema(name = "分享者的名称")
    private String username;


}
