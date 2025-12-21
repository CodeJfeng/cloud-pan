package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

@Data
@Tag(name = "文件复制参数的实体对象")
public class CopyFilePO implements Serializable {

    @Serial
    private static final long serialVersionUID = -5485452451425L;

    @Schema(name = "要复制的文件ID集合，多个使用公用分隔符隔开__，__")
    @NotBlank(message = "请选择需要复制的文件")
    private String fileIds;

    @Schema(name = "要复制的目标父文件夹ID")
    @NotBlank(message = "请选择要复制到哪个目标文件")
    private String targetParentId;

}
