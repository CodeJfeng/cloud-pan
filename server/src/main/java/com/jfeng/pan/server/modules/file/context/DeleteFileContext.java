package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量删除文件上下文实体对象
 */
@Data
public class DeleteFileContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -47519315437L;

    /**
     * 要删除文件的ID几何
     */
    private List<Long> fileIdList;

    /**
     * 当前登录的用户ID
     */
    private Long userId;
}
