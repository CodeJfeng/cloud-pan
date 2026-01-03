package com.jfeng.pan.server.modules.share.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jfeng.pan.web.serializer.Date2StringSerializer;
import com.jfeng.pan.web.serializer.IdEncryptSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Tag(name = "分享链接列表结果的实体对象")
@Data
public class ShareUrlListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = -35975723452365L;

    @JsonSerialize(using = IdEncryptSerializer.class)
    @Schema(name = "分享链接的ID")
    private Long shareId;

    @Schema(name = "分享链接的名称")
    private String shareName;

    @Schema(name = "分享链接的URL")
    private String shareUrl;

    @Schema(name = "分享链接的分享码")
    private String shareCode;

    @Schema(name = "分享链接的状态")
    private Integer shareStatus;

    @Schema(name = "分享链接的类型，（0，有提取码）")
    private Integer shareType;

    @Schema(name = "分享链接的过期类型")
    private Integer shareDayType;

    @Schema(name = "分享链接的结束时间")
    @JsonSerialize(using = Date2StringSerializer.class)
    private Date shareEndTime;

    @Schema(name = "分享链接的创建时间")
    @JsonSerialize(using = Date2StringSerializer.class)
    private Date createTime;

}
