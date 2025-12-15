package com.jfeng.pan.server.modules.file.context;


import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import java.io.Serial;
import java.io.Serializable;

/**
 * 文件分片上传上下文实体
 */
@Data
@Tag(name = "文件分片上传实体")
public class FileChunkUploadContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -4356732395421678L;

    /**
     * 文件名称
     */
    private String filename;

    /**
     * 文件的唯一标识
     */
    private String identifier;

    /**
     * 总分片数
     */
    private Integer totalChunks;

    /**
     * 当前的分片序号
     */
    private Integer chunkNumber;

    /**
     * 当前分片的文件大小
     */
    private Long currentChunkSize;

    /**
     * 文件的总大小
     */
    private Long totalSize;

    /**
     * 文件实体
     */
    private MultipartFile file;

    /**
     * 当前的用户ID
     */
    private Long userId;
}
