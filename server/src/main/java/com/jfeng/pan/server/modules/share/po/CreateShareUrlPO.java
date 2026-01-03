package com.jfeng.pan.server.modules.share.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

@Tag(name = "创建分享链接的参数实体对象")
@Data
public class CreateShareUrlPO implements Serializable {

    @Serial
    private static final long serialVersionUID = -24854012476L;

    @Schema(name = "分享的名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分享的名称不能为空")
    private String shareName;

    @Schema(name = "分享的类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分享的类型不能为空")
    private Integer shareType;

    @Schema(name = "分享的日期类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分享的日期类型不能为空")
    private Integer shareDayType;

    @Schema(name = "分享的文件ID集合，多个使用公用的分隔符进行拼接", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分享的文件ID集合不能为空")
    private String shareFileIds;
}
