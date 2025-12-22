package com.jfeng.pan.server.modules.file.vo;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jfeng.pan.web.serializer.Date2StringSerializer;
import com.jfeng.pan.web.serializer.IdEncryptSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 用户搜索文件对应的实体
 */
@Data
@Tag(name = "用户搜索文件对应的实体")
public class FileSearchResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = -5478565753234L;

    @Schema(description = "文件ID")
    @JsonSerialize(using = IdEncryptSerializer.class)
    private Long fileId;

    @Schema(description = "父文件夹ID")
    @JsonSerialize(using = IdEncryptSerializer.class)
    private Long parentId;

    @Schema(description = "父文件夹名称")
    private String parentFilename;

    @Schema(description = "文件名称")
    private String fileName;

    @Schema(description = "文件大小描述")
    private String fileSizeDesc;

    @Schema(description = "文件夹标识 （ 0 否 1 是）")
    private Integer folderFlag;

    @Schema(description = "文件类型 （1 普通文件 2 压缩文件 3 excel 4 word 5 pdf 6 txt 7 图片 8 音频 9 视频 10 ppt 11 源码文件 12 csv）")
    private Integer fileType;

    @Schema(description = "文件更新时间")
    @JsonSerialize(using = Date2StringSerializer.class)
    private Date updateTime;

}