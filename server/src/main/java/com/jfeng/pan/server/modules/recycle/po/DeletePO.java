package com.jfeng.pan.server.modules.recycle.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * 文件删除的参数实体
 */
@Tag(name = "文件删除参数实体")
@Data
public class DeletePO implements Serializable {
    @Serial
    private static final long serialVersionUID = -587682573453141L;

    @Schema(name = "要删除的文件ID集合" , description = "多个id使用公用分隔符分割__,__", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择要删除的文件")
    private String fileIds;
}
