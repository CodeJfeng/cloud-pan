package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文件搜索上下文实体信息
 */
@Data
public class FileSearchContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -43745612415L;

    /**
     * 搜索关键字
     */
    private String keyword;

    /**
     * 搜索的文件类型集合
     */
    private List<Integer> fileTypeArray;

    /**
     * 当前登录的用户ID
     */
    private Long userId;

    /**
     * 文件删除表示 默认为不删除
     */
    private Integer delFlag = 0;
}

