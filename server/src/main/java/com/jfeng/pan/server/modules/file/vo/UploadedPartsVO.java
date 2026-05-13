package com.jfeng.pan.server.modules.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 已上传分片列表响应
 *
 * @author jfeng
 */
@Data
@Tag(name = "已上传分片列表响应")
public class UploadedPartsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 已上传的分片编号列表
     */
    @Schema(description = "已上传的分片编号列表")
    private List<Integer> uploadedParts;

    /**
     * 文件存储路径（对象键）
     */
    @Schema(description = "文件存储路径")
    private String objectKey;

    /**
     * 分片上传任务ID
     */
    @Schema(description = "分片上传ID")
    private String uploadId;
}
