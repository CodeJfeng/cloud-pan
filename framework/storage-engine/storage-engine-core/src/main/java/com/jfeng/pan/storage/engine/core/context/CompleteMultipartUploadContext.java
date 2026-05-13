package com.jfeng.pan.storage.engine.core.context;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.List;

/**
 * 完成分片上传上下文
 *
 * @author jfeng
 */
@Data
public class CompleteMultipartUploadContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件存储路径（对象键）
     */
    private String objectKey;

    /**
     * 分片上传任务ID
     */
    private String uploadId;

    /**
     * 文件名称
     */
    private String filename;

    /**
     * 文件总大小（字节）
     */
    private Long totalSize;

    /**
     * 文件唯一标识
     */
    private String identifier;

    /**
     * 当前操作用户ID
     */
    private Long userId;

    /**
     * 分片信息列表，包含每个分片的 partNumber 和 eTag
     */
    private List<PartInfo> parts;

    /**
     * 分片信息
     */
    @Data
    public static class PartInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 分片号（从1开始）
         */
        private Integer partNumber;

        /**
         * 分片的 ETag 值
         */
        private String eTag;
    }
}
