package com.jfeng.pan.storage.engine.core;

import cn.hutool.core.lang.Assert;
import com.jfeng.pan.cache.core.constants.CacheConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.storage.engine.core.context.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

/**
 * 顶级文件存储引擎的公用父类
 */
public abstract class AbstractStorageEngine  implements  StorageEngine{

    @Autowired
    private CacheManager cacheManager;

    protected Cache getCache(){
        if(Objects.isNull(cacheManager)){
            throw new RPanBusinessException("this cache manager is empty!");
        }
        return cacheManager.getCache(CacheConstants.R_PAN_CACHE_NAME);
    }

    /**
     * 存储物理文件
     * 1、参数校验
     * 2、执行存储
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
        Assert.notNull(context.getInputStream(),"文件不能为空");

    }

    /**
     * 删除物理文件
     * 1、删除校验
     * 2、执行删除
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
     * @param context
     */
    protected  abstract void doDelete(DeleteFileContext context) throws IOException;

    /**
     * 校验删除物理文件的上下文信息
     * @param context
     */
    private void checkDeleteFileContext(DeleteFileContext context) {
        Assert.notEmpty(context.getRealPathList(),"要删除的文件路径列表不能为空");
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
     * @param context
     */
    protected abstract void doMergeFile(MergeFileContext context) throws IOException;

    /**
     * 检查文件分片合并的上下文实体信息
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
     * @param context
     */
    protected abstract void doReadFile(ReadFileContext context) throws IOException;

    /**
     * 检查读取文件的上下文实体信息
     * @param context
     */
    private void checkReadFileContext(ReadFileContext context) {
        Assert.notBlank(context.getRealPath(), "文件的真实路径不能为空");
        Assert.notNull(context.getOutputStream(), "文件的输出流不能为空");
    }
}
