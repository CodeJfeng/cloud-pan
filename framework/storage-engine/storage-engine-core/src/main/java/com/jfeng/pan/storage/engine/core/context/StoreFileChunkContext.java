package com.jfeng.pan.storage.engine.core.context;

import lombok.Data;

import java.io.InputStream;
import java.io.Serializable;

/**
 * 保存文件分片的上下文信息
 */
@Data
public class StoreFileChunkContext implements Serializable {

    /**
     * 文件名
     */
    private String filename;

    /**
     * 文件唯一标识
     */
    private String identifier;

    /**
     * 文件的总大小
     */
    private Long totalSize;

    /**
     * 文件输入流
     */
    private InputStream inputStream;

    /**
     * 文件的真实存储路径
     */
    private String realPath;

    /**
     * 文件的总分片数
     */
    private Integer totalChunk;

    /**
     * 当前分片的下标
     */
    private Integer chunkNumber;

    /**
     * 当前的分片大小
     */
    private Long currentChunkSize;

    /**
     * 当前登录的用户ID
     */
    private Long userId;
}
