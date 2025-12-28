package com.jfeng.pan.server.modules.recycle.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * 文件还原的PO实体
 */
@Tag(name = "文件还原实体")
@Data
public class RestorePO implements Serializable {
    @Serial
    private static final long serialVersionUID = -1238573453141L;

    @Schema(name = "要还原的文件ID集合" , description = "多个id使用公用分隔符分割__,__", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择要还原的文件")
    private String fileIds;


}
