package com.jfeng.pan.server.modules.share.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

@Tag(name = "取消分享参数的实体对象")
@Data
public class CancelSharePO implements Serializable {

    @Serial
    private static final long serialVersionUID = -96474562345286L;

    @Schema(name = "要取消的分享ID的集合，多个使用公用的分隔符拼接",requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择要取消的分享")
    private String shareIds;
}
