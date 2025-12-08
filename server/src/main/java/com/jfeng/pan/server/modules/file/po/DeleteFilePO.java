package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * 批量删除文件入参对象实体
 */
@Data
@Tag(name = "批量删除文件入参的PO对象")

public class DeleteFilePO implements Serializable {
    @Serial
    private static final long serialVersionUID = -8565395421678L;

    @Schema(description = "要删除的文件ID，多个使用公用分隔符分割", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "删除文件为空")
    private String fileIds;
}
