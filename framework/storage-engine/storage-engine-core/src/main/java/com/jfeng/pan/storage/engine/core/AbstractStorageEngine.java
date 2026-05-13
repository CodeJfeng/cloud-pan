package com.jfeng.pan.storage.engine.core;

import cn.hutool.core.lang.Assert;
import com.jfeng.pan.cache.core.constants.CacheConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.storage.engine.core.context.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.io.IOException;
import java.util.Objects;

/**
 * 顶级文件存储引擎的公用父类
 */
public abstract class AbstractStorageEngine implements StorageEngine {

    @Autowired
    private CacheManager cacheManager;

    protected Cache getCache() {
        if (Objects.isNull(cacheManager)) {
            throw new RPanBusinessException("this cache manager is empty!");
        }
        return cacheManager.getCache(CacheConstants.R_PAN_CACHE_NAME);
    }

    /**
     * 存储物理文件
     * 1、参数校验
     * 2、执行存储
     * 
     * @param context
     * @throws IOException
     */
    @Override
    public void store(StoreFileContext context) throws IOException {
        checkStoreFileContext(context);
        doStore(context);
    }

    /**
     * 执行保存物理文件的动作
     * 下称到具体的子类去实现
     * 
     * @param context
     * @throws IOException
     */
    protected abstract void doStore(StoreFileContext context) throws IOException;

    /**
     * 校验上传物理文件的上下文信息
     *
     * @param context
     */
    private void checkStoreFileContext(StoreFileContext context) {
        Assert.notBlank(context.getFilename(), "文件名不能为空");
        Assert.notNull(context.getTotalSize(), "文件总大小不能为空");
        Assert.notNull(context.getInputStream(), "文件不能为空");

    }

    /**
     * 删除物理文件
     * 1、删除校验
     * 2、执行删除
     * 
     * @param context
     * @throws IOException
     */
    @Override
    public void delete(DeleteFileContext context) throws IOException {
        checkDeleteFileContext(context);
        doDelete(context);

    }

    /**
     * 执行删除物理文件的动作
     * 下沉到子类去实现
     * 
     * @param context
     */
    protected abstract void doDelete(DeleteFileContext context) throws IOException;

    /**
     * 校验删除物理文件的上下文信息
     * 
     * @param context
     */
    private void checkDeleteFileContext(DeleteFileContext context) {
        Assert.notEmpty(context.getRealPathList(), "要删除的文件路径列表不能为空");
    }

    /**
     * 存储物理文件的分片
     * 1、参数校验
     * 2、执行动作
     *
     * @param context
     * @throws IOException
     */
    @Override
    public void storeChunk(StoreFileChunkContext context) throws IOException {
        checkStoreFileChunk(context);
        doStoreChunk(context);
    }

    /**
     * 执行保存文件分片
     * 下沉到底层进行实现
     *
     * @param context
     * @throws IOException
     */
    protected abstract void doStoreChunk(StoreFileChunkContext context) throws IOException;

    /**
     * 校验保存分片的文件参数
     * 
     * @param context
     */
    private void checkStoreFileChunk(StoreFileChunkContext context) {
        Assert.notBlank(context.getFilename(), "文件名不能为空");
        Assert.notBlank(context.getIdentifier(), "文件唯一标识不能为空");
        Assert.notNull(context.getTotalSize(), "文件大小不能为空");
        Assert.notNull(context.getInputStream(), "文件分片不能为空");
        Assert.notNull(context.getTotalChunks(), "文件分片总数不能为空");
        Assert.notNull(context.getChunkNumber(), "文件分片下标不能为空");
        Assert.notNull(context.getCurrentChunkSize(), "文件分片的大小不能为空");
        Assert.notNull(context.getUserId(), "当前登录用户ID不能为空");
    }

    /**
     * 合并文件分片
     * 1、检查参数
     * 2、执行动作
     *
     * @param context
     * @throws IOException
     */
    @Override
    public void mergeFile(MergeFileContext context) throws IOException {
        checkMergeFileContext(context);
        doMergeFile(context);
    }

    /**
     * 执行文件分片的动作
     * 下沉到子类实现
     * 
     * @param context
     */
    protected abstract void doMergeFile(MergeFileContext context) throws IOException;

    /**
     * 检查文件分片合并的上下文实体信息
     * 
     * @param context
     */
    private void checkMergeFileContext(MergeFileContext context) {
        Assert.notBlank(context.getFilename(), "文件名不能为空");
        Assert.notBlank(context.getIdentifier(), "文件唯一标识不能为空");
        Assert.notNull(context.getUserId(), "当前登录用户ID不能为空");
        Assert.notEmpty(context.getRealPathList(), "文件分片列表不能为空");
    }

    /**
     * 读取文件内容写入到输出流中
     * 1、参数校验
     * 2、执行动作
     *
     * @param context
     * @throws IOException
     */
    @Override
    public void readFile(ReadFileContext context) throws IOException {
        checkReadFileContext(context);
        doReadFile(context);
    }

    /**
     * 读取文件内容并写入到输出流中
     * 下沉到子类实现
     * 
     * @param context
     */
    protected abstract void doReadFile(ReadFileContext context) throws IOException;

    /**
     * 检查读取文件的上下文实体信息
     * 
     * @param context
     */
    private void checkReadFileContext(ReadFileContext context) {
        Assert.notBlank(context.getRealPath(), "文件的真实路径不能为空");
        Assert.notNull(context.getOutputStream(), "文件的输出流不能为空");
    }

    /**
     * 生成单文件上传预签名URL
     * 1、参数校验
     * 2、生成预签名URL
     *
     * @param context 预签名URL生成上下文，包含文件名、文件大小、用户ID等信息
     * @return 预签名上传URL
     */
    @Override
    public String generatePresignedUploadUrl(GeneratePresignedUrlContext context) {
        checkGeneratePresignedUrlContext(context);
        return doGeneratePresignedUploadUrl(context);
    }

    /**
     * 执行生成单文件上传预签名URL的具体逻辑
     * 下沉到子类实现
     *
     * @param context 预签名URL生成上下文
     * @return 预签名上传URL
     */
    protected abstract String doGeneratePresignedUploadUrl(GeneratePresignedUrlContext context);

    /**
     * 校验生成预签名URL的上下文参数
     *
     * @param context 预签名URL生成上下文
     */
    private void checkGeneratePresignedUrlContext(GeneratePresignedUrlContext context) {
        Assert.notBlank(context.getFilename(), "文件名不能为空");
        Assert.notNull(context.getTotalSize(), "文件总大小不能为空");
        Assert.notNull(context.getUserId(), "当前登录用户ID不能为空");
    }

    /**
     * 生成分片上传初始化预签名URL
     * 1、参数校验
     * 2、生成初始化预签名URL
     *
     * @param context 分片上传初始化上下文，包含文件名、文件大小、分片总数、用户ID等信息
     * @return 分片上传初始化预签名URL
     */
    @Override
    public String generatePresignedMultipartInitUrl(GeneratePresignedMultipartUrlContext context) {
        checkGeneratePresignedMultipartUrlContext(context);
        return doGeneratePresignedMultipartInitUrl(context);
    }

    /**
     * 执行生成分片上传初始化预签名URL的具体逻辑
     * 下沉到子类实现
     *
     * @param context 分片上传初始化上下文
     * @return 分片上传初始化预签名URL
     */
    protected abstract String doGeneratePresignedMultipartInitUrl(GeneratePresignedMultipartUrlContext context);

    /**
     * 校验生成分片上传初始化预签名URL的上下文参数
     *
     * @param context 分片上传初始化上下文
     */
    private void checkGeneratePresignedMultipartUrlContext(GeneratePresignedMultipartUrlContext context) {
        Assert.notBlank(context.getFilename(), "文件名不能为空");
        Assert.notNull(context.getTotalSize(), "文件总大小不能为空");
        Assert.notNull(context.getTotalChunks(), "分片总数不能为空");
        Assert.notNull(context.getUserId(), "当前登录用户ID不能为空");
    }

    /**
     * 生成分片上传预签名URL
     * 1、参数校验
     * 2、生成指定分片的预签名URL
     *
     * @param context 分片上传上下文，包含objectKey、uploadId、分片号、用户ID等信息
     * @return 分片上传预签名URL
     */
    @Override
    public String generatePresignedPartUploadUrl(GeneratePresignedPartUrlContext context) {
        checkGeneratePresignedPartUrlContext(context);
        return doGeneratePresignedPartUploadUrl(context);
    }

    /**
     * 执行生成分片上传预签名URL的具体逻辑
     * 下沉到子类实现
     *
     * @param context 分片上传上下文
     * @return 分片上传预签名URL
     */
    protected abstract String doGeneratePresignedPartUploadUrl(GeneratePresignedPartUrlContext context);

    /**
     * 校验生成分片上传预签名URL的上下文参数
     *
     * @param context 分片上传上下文
     */
    private void checkGeneratePresignedPartUrlContext(GeneratePresignedPartUrlContext context) {
        Assert.notBlank(context.getObjectKey(), "objectKey不能为空");
        Assert.notBlank(context.getUploadId(), "uploadId不能为空");
        Assert.notNull(context.getPartNumber(), "分片号不能为空");
        Assert.notNull(context.getUserId(), "当前登录用户ID不能为空");
    }

    /**
     * 完成分片上传并合并文件
     * 1、参数校验
     * 2、执行合并操作
     *
     * @param context 完成分片上传上下文，包含objectKey、uploadId、文件名、文件大小、用户ID等信息
     * @throws IOException 合并文件时可能发生的IO异常
     */
    @Override
    public void completeMultipartUpload(CompleteMultipartUploadContext context) throws IOException {
        checkCompleteMultipartUploadContext(context);
        doCompleteMultipartUpload(context);
    }

    /**
     * 执行完成分片上传的具体逻辑
     * 下沉到子类实现
     *
     * @param context 完成分片上传上下文
     * @throws IOException 合并文件时可能发生的IO异常
     */
    protected abstract void doCompleteMultipartUpload(CompleteMultipartUploadContext context) throws IOException;

    /**
     * 校验完成分片上传的上下文参数
     *
     * @param context 完成分片上传上下文
     */
    private void checkCompleteMultipartUploadContext(CompleteMultipartUploadContext context) {
        Assert.notBlank(context.getObjectKey(), "objectKey不能为空");
        Assert.notBlank(context.getUploadId(), "uploadId不能为空");
        Assert.notBlank(context.getFilename(), "文件名不能为空");
        Assert.notNull(context.getTotalSize(), "文件总大小不能为空");
        Assert.notNull(context.getUserId(), "当前登录用户ID不能为空");
    }

    /**
     * 查询已上传的分片列表
     * 1、参数校验
     * 2、查询已上传分片
     *
     * @param context 查询已上传分片上下文
     * @return 已上传的分片编号列表
     */
    @Override
    public java.util.List<Integer> listUploadedParts(ListUploadedPartsContext context) {
        checkListUploadedPartsContext(context);
        return doListUploadedParts(context);
    }

    /**
     * 校验查询已上传分片上下文
     *
     * @param context 查询已上传分片上下文
     */
    private void checkListUploadedPartsContext(ListUploadedPartsContext context) {
        Assert.notNull(context, "查询已上传分片上下文不能为空");
        Assert.notBlank(context.getObjectKey(), "objectKey不能为空");
        Assert.notBlank(context.getUploadId(), "uploadId不能为空");
    }

    /**
     * 执行查询已上传分片的具体逻辑
     * 下沉到子类实现
     *
     * @param context 查询已上传分片上下文
     * @return 已上传的分片编号列表
     */
    protected abstract java.util.List<Integer> doListUploadedParts(ListUploadedPartsContext context);
}
