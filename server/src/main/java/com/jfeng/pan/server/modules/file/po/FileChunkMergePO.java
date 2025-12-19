package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

@Tag(name = "文件分片合并参数对象")
@Data
public class FileChunkMergePO implements Serializable {

    @Serial
    private static final long serialVersionUID = -8454123485412L;

    @Schema(name = "文件名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件名称不能为空")
    private String filename;

    @Schema(name = "文件的唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件唯一标识不能为空")
    private String identifier;

    @Schema(name = "文件总大小", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件总大小不能为空")
    private Long totalSize;

    @Schema(name = "父文件夹Id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "父文件夹Id不能为空")
    private String parentId;

}
