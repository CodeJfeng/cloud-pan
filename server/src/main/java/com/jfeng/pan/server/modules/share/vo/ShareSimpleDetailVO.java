package com.jfeng.pan.server.modules.share.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Tag(name = "查询分享简单详情返回实体对象")
@Data
public class ShareSimpleDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = -845634512394364L;

    @Schema(name = "分享ID")
    private Long shareId;

    @Schema(name = "分享名称")
    private String shareName;

    @Schema(name = "分享人信息")
    private ShareUserInfoVO shareUserInfoVO;
}
