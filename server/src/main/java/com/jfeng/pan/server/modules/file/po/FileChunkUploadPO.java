package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
/**
 * 文件分片上传PO实体对象
 */
@Data
@Tag(name = "文件分片上传实体")
public class FileChunkUploadPO implements Serializable {

    @Serial
    private static final long serialVersionUID = -54732395421678L;

    @Schema(name = "文件名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件名不能为空")
    private String filename;

    @Schema(name = "文件唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件唯一标识不能为空")
    private String identifier;

    @Schema(name = "文件分片总数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "总分片数量不能为空")
    private Integer totalChunks;

    @Schema(name = "当前文件分片序号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "当前文件分片序号不能为空")
    private Integer chunkNumber;

    @Schema(name = "文件当前分片的大小", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件当前分片的大小不能为空")
    private Long currentChunkSize;

    @Schema(name = "文件总大小", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件总大小不能为空")
    private Long totalSize;

    @Schema(name = "分片文件实体", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分片文件实体不能为空")
    private MultipartFile file;

}
