package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * 文件重命名的PO对象
 */
@Data
public class UpdateFilenamePO implements Serializable {
    @Serial
    private static final long serialVersionUID = -861234656452L;

    @Schema(description = "更新的文件ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "更新的文件ID不能为空")
    private String fileId;

    @Schema(description = "更新的文件名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "更新的文件名称不能为空")
    private String newFileName;

}
