package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询文件夹树的上下文实体信息
 */
@Data
public class QueryFolderTreeContext implements Serializable {
    @Serial
    private static final long serialVersionUID = -9346412675124L;

    /**
     * 当前登录的用户ID
     */
    private Long userId;
}
