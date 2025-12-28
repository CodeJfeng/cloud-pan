package com.jfeng.pan.server.modules.recycle.context;

import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文件删除的上下文实体对象
 */
@Data
public class DeleteContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -45696723534385L;

    /**
     * 要删除的文件ID的集合
     */
    private List<Long> fileIdList;

    /**
     * 当前登录的用户ID
     */
    private long userId;

    /**
     * 要被还原的文件记录列表
     */
    private List<RPanUserFile> records;

    /**
     * 所有要被删除的文件记录列表
     */
    private List<RPanUserFile> allRecords;
}
