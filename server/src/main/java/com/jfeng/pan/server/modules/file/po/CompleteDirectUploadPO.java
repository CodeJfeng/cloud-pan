package com.jfeng.pan.server.modules.file.po;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.List;

@Data
@Tag(name = "完成直传回调参数")
public class CompleteDirectUploadPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "objectKey", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "objectKey不能为空")
    private String objectKey;

    @Schema(description = "uploadId（分片上传时必填）")
    private String uploadId;

    @Schema(description = "文件名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件名称不能为空")
    private String filename;

    @Schema(description = "文件总大小", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件总大小不能为空")
    private Long totalSize;

    @Schema(description = "文件唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件唯一标识不能为空")
    private String identifier;

    @Schema(description = "父文件夹ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "父文件夹ID不能为空")
    private String parentId;

    @Schema(description = "分片信息列表（分片上传时必填）")
    private List<PartInfo> parts;

    @Data
    @Schema(description = "分片信息")
    public static class PartInfo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "分片号（从1开始）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "分片号不能为空")
        private Integer partNumber;

        @Schema(description = "分片的 ETag 值", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "ETag不能为空")
        private String eTag;
    }
}
