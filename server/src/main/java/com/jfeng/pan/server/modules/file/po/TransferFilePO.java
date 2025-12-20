package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

@Data
@Tag(name = "文件转移功能的PO对象")
public class TransferFilePO implements Serializable {

    @Serial
    private static final long serialVersionUID = -575912451425L;

    @Schema(name = "要转移的文件ID集合，多个使用公用分隔符隔开__，__")
    @NotBlank(message = "请选择需要转移的文件")
    private String fileIds;

    @Schema(name = "要转移的目标父文件夹ID")
    @NotBlank(message = "请选择要转移到哪个目标文件")
    private String targetParentId;

}
