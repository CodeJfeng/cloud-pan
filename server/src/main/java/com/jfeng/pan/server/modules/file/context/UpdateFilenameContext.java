package com.jfeng.pan.server.modules.file.context;

import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文件重命名的上下文对象
 */
@Data
public class UpdateFilenameContext implements Serializable {
    @Serial
    private static final long serialVersionUID = -86157564656452L;


    /**
     * 需要更新的文件ID
     */
    private Long fileId;

    /**
     * 当前的登录用户id
     */
    private Long userId;

    /**
     * 新的文件名称
     */
    private String newFilename;

    /**
     * 当前需要更新的用户实体
     */
    private RPanUserFile entity;

}
