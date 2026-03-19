package com.jfeng.pan.server.modules.user.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户查询搜索历史记录上下文实体
 */
@Data
public class QueryUserSearchHistoryContext implements Serializable {
    @Serial
    private static final long serialVersionUID = -238564858412L;

    /**
     * 当前登录用户的userID
     */
    private Long userId;
}
