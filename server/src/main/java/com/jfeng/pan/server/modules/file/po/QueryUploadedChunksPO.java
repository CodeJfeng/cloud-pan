package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

@Data
@Tag(name = "查询用户已上传的分片列表的参数实体")
public class QueryUploadedChunksPO implements Serializable {

    @Serial
    private static final long serialVersionUID = -12543712345L;

    @Schema(name = "文件的唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件标识不能为空")
    private String identifier;

}
