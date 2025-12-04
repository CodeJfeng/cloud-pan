package com.jfeng.pan.server.modules.file.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jfeng.pan.web.serializer.IdEncryptSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@Tag(name="文件列表响应实体", description = "后端向前端返回文件列表实体信息")
public class RPanUserFileVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -889431231L;

    @Schema(description = "文件ID")
    @JsonSerialize(using = IdEncryptSerializer.class)
    private Long fileId;

    @Schema(description = "父文件夹ID")
    @JsonSerialize(using = IdEncryptSerializer.class)
    private Long parentId;

    @Schema(description = "文件名称")
    private String fileName;

    @Schema(description = "文件大小描述")
    private String fileSizeDesc;

    @Schema(description = "文件夹标识 （ 0 否 1 是）")
    private Integer folderFlag;

    @Schema(description = "文件类型 （1 普通文件 2 压缩文件 3 excel 4 word 5 pdf 6 txt 7 图片 8 音频 9 视频 10 ppt 11 源码文件 12 csv）")
    private Integer fileType;

    @Schema(description = "文件更新时间")
    private Date updateTime;
}
