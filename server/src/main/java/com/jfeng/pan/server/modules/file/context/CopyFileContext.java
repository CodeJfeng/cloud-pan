package com.jfeng.pan.server.modules.file.context;

import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文件复制操作的上下文实体对象
 */
@Data
public class CopyFileContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -43588458624976564L;

    /**
     * 要复制的文件ID集合
     */
    private List<Long> fileIdList;

    /**
     * 目标文件夹ID
     */
    private Long targetParentId;

    /**
     * 当前登录的用户ID
     */
    private Long userId;

    /**
     * 要复制的文件列表
     */
    private List<RPanUserFile> prepareRecords;
}
