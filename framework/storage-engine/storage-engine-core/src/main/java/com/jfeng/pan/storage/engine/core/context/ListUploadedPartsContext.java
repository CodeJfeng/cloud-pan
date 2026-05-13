package com.jfeng.pan.storage.engine.core.context;

import lombok.Data;

import java.io.Serializable;

/**
 * 查询已上传分片上下文
 *
 * @author jfeng
 */
@Data
public class ListUploadedPartsContext implements Serializable {

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
     * 当前操作用户ID
     */
    private Long userId;
}
