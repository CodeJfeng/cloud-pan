package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询用户已上传的分片列表
 */
@Data
public class QueryUploadedChunksContext implements Serializable {
    @Serial
    private static final long serialVersionUID = -12543756785635L;

    /**
     * 文件的唯一标识
     */
    private String identifier;

    /**
     * 当前登录的用户ID
     */
    private Long userId;
}
