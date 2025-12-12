package com.jfeng.pan.server.modules.file.context;

import com.jfeng.pan.server.modules.file.entity.RPanFile;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serial;
import java.io.Serializable;

@Data
public class FileUploadContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -58795733613345L;

    /**
     * 文件名称
     */
    private String filename;

    /**
     * 文件的唯一标识
     */
    private String identifier;


    /**
     * 文件的总大小
     */
    private Long totalSize;

    /**
     * 父文件夹Id
     */
    private Long parentId;

    /**
     * 文件实体
     */
    private MultipartFile file;

    /**
     * 当前登录用户Id
     */
    private Long userId;

    /**
     * 实体文件记录
     */
    private RPanFile recode;
}
