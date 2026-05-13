package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * 生成分片上传预签名URL请求参数
 *
 * @author jfeng
 */
@Data
@Tag(name = "生成分片上传预签名URL参数")
public class GeneratePresignedPartUrlPO implements Serializable {

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

    /**
     * 分片编号（从1开始）
     */
    @Schema(description = "分片号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分片号不能为空")
    private Integer partNumber;

    /**
     * 当前分片大小（字节）
     */
    @Schema(description = "分片大小", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分片大小不能为空")
    private Long partSize;
}
