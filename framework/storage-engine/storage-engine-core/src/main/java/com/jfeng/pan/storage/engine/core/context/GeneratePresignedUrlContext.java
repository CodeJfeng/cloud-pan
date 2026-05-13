package com.jfeng.pan.storage.engine.core.context;

import lombok.Data;

import java.io.Serializable;

/**
 * 生成单文件上传预签名URL上下文
 *
 * @author jfeng
 */
@Data
public class GeneratePresignedUrlContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件名称
     */
    private String filename;

    /**
     * 文件总大小（字节）
     */
    private Long totalSize;

    /**
     * 文件MIME类型
     */
    private String contentType;

    /**
     * 当前操作用户ID
     */
    private Long userId;
}
