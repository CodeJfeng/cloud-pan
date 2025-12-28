package com.jfeng.pan.server.modules.recycle.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询用户回收站文件列表上下文实体对象
 */
@Data
public class QueryRecycleFileListContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -43741254548523L;

    /**
     * 当前登录的用户ID
     */
    private Long userId;
}
