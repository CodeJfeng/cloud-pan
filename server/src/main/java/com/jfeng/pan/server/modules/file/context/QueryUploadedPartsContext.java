package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询已上传分片列表上下文
 *
 * @author jfeng
 */
@Data
public class QueryUploadedPartsContext implements Serializable {

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
     * 当前操作用户ID
     */
    private Long userId;
}
