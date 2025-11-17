package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建文件夹上下文实体
 */
@Data
public class CreateFolderContext implements Serializable {
    @Serial
    private  static final long serialVersionUID = -123123124124L;

    /**
     * 父文件夹ID
     */
    private Long parentId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 文件夹名称
     */
    private String folderName;
}
