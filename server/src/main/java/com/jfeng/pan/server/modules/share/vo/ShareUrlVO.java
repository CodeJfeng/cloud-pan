package com.jfeng.pan.server.modules.share.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jfeng.pan.web.serializer.IdEncryptSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Tag(name = "创建分享链接的返回实体对象")
@Data
public class ShareUrlVO implements Serializable {

    @Serial
    private static final long serialVersionUID = -4795427401456L;

    @JsonSerialize(using = IdEncryptSerializer.class)
    @Schema(name = "分享链接的ID")
    private Long shareId;

    @Schema(name = "分享链接的名称")
    private String shareName;

    @Schema(name = "分享链接的url")
    private String shareUrl;

    @Schema(name = "分享链接的分享码")
    private String shareCode;

    @Schema(name = "分享链接的状态")
    private String shareStatus;


}
