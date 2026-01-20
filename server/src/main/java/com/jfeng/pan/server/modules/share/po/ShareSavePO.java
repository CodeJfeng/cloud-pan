package com.jfeng.pan.server.modules.share.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

@Tag(name = "保存到我的网盘参数实体对象")
@Data
public class ShareSavePO implements Serializable {
    @Serial
    private static final long serialVersionUID = -1283475454124L;

    @Schema(name = "要转存的文件ID集合，多个使用公用分割符进行拼接", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择要保存的文件")
    private String fileIds;

    @Schema(name = "目标父文件夹ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择要保存到的文件夹")
    private String targetParentId;
}
