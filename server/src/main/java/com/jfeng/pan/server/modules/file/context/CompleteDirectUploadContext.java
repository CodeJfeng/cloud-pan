package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.List;

@Data
public class CompleteDirectUploadContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String objectKey;

    private String uploadId;

    private String filename;

    private Long totalSize;

    private String identifier;

    private Long parentId;

    private Long userId;

    /**
     * 分片信息列表，包含每个分片的 partNumber 和 eTag
     */
    private List<PartInfo> parts;

    @Data
    public static class PartInfo implements Serializable {

        @Serial
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
