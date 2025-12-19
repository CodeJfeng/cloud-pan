package com.jfeng.pan.server.modules.file.context;

import com.jfeng.pan.server.modules.file.entity.RPanFile;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文件分片合并的上下文实体对象
 */
@Data
public class FileChunkMergeAndSaveContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -478231856234412L;

    /**
     * 文件名称
     */
    private String filename;

    /**
     * 文件的唯一标识
     */
    private String identifier;

    /**
     * 文件总大小
     */
    private Long totalSize;

    /**
     * 父文件夹Id
     */
    private Long parentId;

    /**
     * 当前登录的用户Id
     */
    private Long userId;

    /**
     * 物理文件记录
     */
    private RPanFile record;

    /**
     * 文件合并之后存储的真实地址
     */
    private String realPath;
}
