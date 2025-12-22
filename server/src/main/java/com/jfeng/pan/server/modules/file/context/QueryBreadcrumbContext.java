package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 搜索文件面包屑列表的上下文实体信息
 */
@Data
public class QueryBreadcrumbContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 5013912772192515218L;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 当前登录的用户ID
     */
    private Long userId;

}
