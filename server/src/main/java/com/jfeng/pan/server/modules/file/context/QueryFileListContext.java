package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 查询文件列表上下文实体
 */
@Data
public class QueryFileListContext implements Serializable {

    @Serial
    private final static long serialVersionUID = -8643512231L;

    /**
     * 父文件夹ID
     */
    private Long parentId;

    /**
     * 文件列表集合
     */
    private List<Integer> fileTypeArray;

    /**
     * 当前登录用户
     */
    private Long userId;

    /**
     * 文件删除标识
     */
    private Integer delFlag;

    /**
     * 文件ID集合
     */
    private List<Long> fileIdList;
}
