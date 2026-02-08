package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * 创建文件夹的PO对象
 */
@Data
@Tag(name="创建文件夹的PO对象")
public class CreateFolderPO implements Serializable {
    @Serial
    private static final long serialVersionUID = -856345421678L;

    @Schema(description = "加密后的父文件夹id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "父文件夹id不能为空")
    private String parentId;

    @Schema(description = "文件夹名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件夹名称不能为空")
    private String folderName;
}
