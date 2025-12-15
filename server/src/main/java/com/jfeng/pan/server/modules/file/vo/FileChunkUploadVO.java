package com.jfeng.pan.server.modules.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serializable;

@Tag(name = "文件分片上传的相应实体")
@Data
public class FileChunkUploadVO implements Serializable {

    @Schema(name = "是否需要合并文件 （0 不需要 1 需要）")
    private Integer mergeFlag;
}
