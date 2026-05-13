package com.jfeng.pan.storage.engine.core;

import com.jfeng.pan.storage.engine.core.context.*;

import java.io.IOException;

/**
 * 文件存储引擎接口
 * 定义了文件存储、删除、分片上传、读取等核心操作
 *
 * @author jfeng
 */
public interface StorageEngine {

    /**
     * 存储物理文件
     *
     * @param context 文件存储上下文，包含文件名、文件大小、文件输入流等信息
     * @throws IOException 文件读写异常或存储系统通信异常
     */
    void store(StoreFileContext context) throws IOException;

    /**
     * 删除物理文件
     *
     * @param context 文件删除上下文，包含要删除的文件路径列表
     * @throws IOException 文件删除异常或存储系统通信异常
     */
    void delete(DeleteFileContext context) throws IOException;

    /**
     * 存储文件分片
     * 用于大文件分片上传场景，每个分片独立上传并记录元数据
     *
     * @param context 文件分片存储上下文，包含文件名、文件唯一标识、分片序号、分片大小等信息
     * @throws IOException 文件分片读写异常或存储系统通信异常
     */
    void storeChunk(StoreFileChunkContext context) throws IOException;

    /**
     * 合并文件分片
     * 在所有分片上传完成后，将分片合并为完整的文件
     *
     * @param context 文件分片合并上下文，包含文件名、文件唯一标识、分片路径列表等信息
     * @throws IOException 文件合并异常或存储系统通信异常
     */
    void mergeFile(MergeFileContext context) throws IOException;

    /**
     * 读取文件内容并写入到输出流
     * 用于文件下载场景
     *
     * @param context 文件读取上下文，包含文件真实路径、输出流等信息
     * @throws IOException 文件读取异常或存储系统通信异常
     */
    void readFile(ReadFileContext context) throws IOException;

    /**
     * 生成单文件上传预签名URL
     * 用于客户端直传场景，服务端签发预签名URL，客户端直接上传到存储系统
     *
     * @param context 预签名URL生成上下文，包含文件名、文件大小、MIME类型等信息
     * @return 预签名上传URL
     */
    String generatePresignedUploadUrl(GeneratePresignedUrlContext context);

    /**
     * 生成分片上传初始化预签名URL
     * 用于大文件分片直传场景，返回uploadId用于后续分片上传
     *
     * @param context 分片上传初始化上下文，包含文件名、文件大小、分片总数等信息
     * @return 分片上传初始化预签名URL，格式为：uploadUrl|objectKey|uploadId|cacheKey
     */
    String generatePresignedMultipartInitUrl(GeneratePresignedMultipartUrlContext context);

    /**
     * 生成分片上传预签名URL
     * 用于客户端直传单个分片到存储系统
     *
     * @param context 分片上传上下文，包含objectKey、uploadId、分片号、分片大小等信息
     * @return 分片上传预签名URL
     */
    String generatePresignedPartUploadUrl(GeneratePresignedPartUrlContext context);

    /**
     * 完成分片上传并合并文件
     * 在所有分片上传完成后，通知存储系统合并分片为完整文件
     *
     * @param context 完成分片上传上下文，包含objectKey、uploadId、分片信息列表等
     * @throws IOException 文件合并异常或存储系统通信异常
     */
    void completeMultipartUpload(CompleteMultipartUploadContext context) throws IOException;
}
