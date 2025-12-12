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
 * 单文件上传的PO实体对象
 */
@Data
@Tag(name = "单文件上传的PO实体对象")
public class FileUploadPO implements Serializable {
    @Serial
    private static final long serialVersionUID = -56423613345L;

    @Schema(name = "文件名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件名称不能为空")
    private String filename;

    @Schema(name = "文件的唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件唯一标识不能为空")
    private String identifier;

    @Schema(name = "文件的总大小", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件的总大小不能为空")
    private Long totalSize;

    @Schema(name = "父文件夹ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "父文件夹ID不能为空")
    private String parentId;

    @Schema(name = "文件实体", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件实体不能为空")
    private MultipartFile file;
}
