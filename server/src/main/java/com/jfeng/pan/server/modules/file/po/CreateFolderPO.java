package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 *
 */
@Data
public class CreateFolderPO implements Serializable {
    @Serial
    private static final long serialVersionUID = -856345421678L;

    @Schema(description = "加密后的父文件夹id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "父文件夹id不能为空")
    private String parentId;

    @Schema(description = "文件夹名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件夹名称不能为空")
    private String fileName;
}
