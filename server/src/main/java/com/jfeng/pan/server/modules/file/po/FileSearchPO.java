package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

@Tag(name = "文件搜索参数实体")
@Data
public class FileSearchPO implements Serializable {
    @Serial
    private static final long serialVersionUID = -547123965344L;

    @Schema(name = "搜索的关键字")
    @NotBlank(message = "搜索关键字不能为空")
    private String keyword;

    @Schema(name = "文件类型，多个文件类型使用公用分隔符__,__拼接")
    private String fileTypes;
}
