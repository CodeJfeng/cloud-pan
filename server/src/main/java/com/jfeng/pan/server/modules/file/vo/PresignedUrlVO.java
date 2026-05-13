package com.jfeng.pan.server.modules.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预签名URL响应结果
 *
 * @author jfeng
 */
@Data
@Tag(name = "预签名URL响应")
public class PresignedUrlVO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * 预签名上传URL，客户端使用此URL直接上传文件
   */
  @Schema(description = "预签名上传URL")
  private String uploadUrl;

  /**
   * 文件存储路径（对象键），用于后续完成上传时标识文件位置
   */
  @Schema(description = "文件存储路径")
  private String objectKey;

  /**
   * 分片上传任务ID，仅在分片上传初始化时返回
   */
  @Schema(description = "分片上传ID（仅分片上传时返回）")
  private String uploadId;

  /**
   * 缓存Key，用于分片上传时缓存分片信息，仅在分片上传初始化时返回
   */
  @Schema(description = "缓存Key（仅分片上传时返回）")
  private String cacheKey;
}
