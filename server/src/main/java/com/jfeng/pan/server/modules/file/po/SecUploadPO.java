package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * 文件秒传PO对象
 */
@Data
@Tag(name = "文件秒传的PO对象")
public class SecUploadPO implements Serializable {

    @Serial
    private static final long serialVersionUID = -2135431254L;

    @Schema(description = "父文件夹ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "父文件夹ID不能为空")
    private String parentId;

    @Schema(description = "文件名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件名称不能为空")
    private String filename;

    @Schema(description = "文件的唯一一表示", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件的唯一一表示不能为空")
    private String identifier;

}
