package com.jfeng.pan.server.modules.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Tag(name = "查询用户已上传的文件分片列表")
public class UploadedChunksVO implements Serializable {

    @Schema(name = "已上传的分片列表")
    private List<Integer> uploadedChunks;
}
