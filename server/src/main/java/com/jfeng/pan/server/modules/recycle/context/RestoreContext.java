package com.jfeng.pan.server.modules.recycle.context;

import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文件还原的上下文实体对象
 */
@Data
public class RestoreContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -2385658134385L;

    /**
     * 要操作的文件ID的集合
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
}
