package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * 生成单文件预签名URL请求参数
 *
 * @author jfeng
 */
@Data
@Tag(name = "生成单文件预签名URL参数")
public class GeneratePresignedUrlPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件名称
     */
    @Schema(description = "文件名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件名称不能为空")
    private String filename;

    /**
     * 文件总大小（字节）
     */
    @Schema(description = "文件总大小", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件总大小不能为空")
    private Long totalSize;

    /**
     * 文件MIME类型
     */
    @Schema(description = "文件类型")
    private String contentType;
}
