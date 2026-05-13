package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 生成分片上传预签名URL上下文
 *
 * @author jfeng
 */
@Data
public class GeneratePresignedPartUrlContext implements Serializable {

    @Serial
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
     * 分片编号（从1开始）
     */
    private Integer partNumber;

    /**
     * 当前分片大小（字节）
     */
    private Long partSize;

    /**
     * 当前操作用户ID
     */
    private Long userId;
}
