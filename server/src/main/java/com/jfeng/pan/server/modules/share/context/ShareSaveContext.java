package com.jfeng.pan.server.modules.share.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 保存到我的网盘的上下文实体对象
 */
@Data
public class ShareSaveContext implements Serializable {
    @Serial
    private static final long serialVersionUID = -958732841245L;

    /**
     * 要转存的文件ID集合
     */
    private List<Long> fileIdList;

    /**
     * 目标文件夹ID
     */
    private Long parentId;

    /**
     * 当前登录的用户ID
     */
    private Long userId;

    /**
     * 分享ID
     */
    private Long shareId;
}
