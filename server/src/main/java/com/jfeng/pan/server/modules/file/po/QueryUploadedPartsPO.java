package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * 查询已上传分片列表参数
 *
 * @author jfeng
 */
@Data
@Tag(name = "查询已上传分片列表参数")
public class QueryUploadedPartsPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件存储路径（对象键）
     */
    @Schema(description = "objectKey", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "objectKey不能为空")
    private String objectKey;

    /**
     * 分片上传任务ID
     */
    @Schema(description = "uploadId", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "uploadId不能为空")
    private String uploadId;
}
