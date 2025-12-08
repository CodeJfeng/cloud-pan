package com.jfeng.pan.server.modules.file.context;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;

/**
 * 文件秒传上下文实体
 */
@Data
public class SecUploadContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -56234543124L;

    /**
     * 父文件ID
     */
    private Long parentId;

    /**
     * 文件名称
     */
    private String filename;

    /**
     * 文件的唯一标识
     */
    private String identifier;

    /**
     * 当前用户唯一标识
     */
    private Long userId;
}
