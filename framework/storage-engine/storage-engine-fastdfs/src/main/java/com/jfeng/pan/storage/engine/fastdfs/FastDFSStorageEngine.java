package com.jfeng.pan.storage.engine.fastdfs;

import com.jfeng.pan.storage.engine.core.AbstractStorageEngine;
import com.jfeng.pan.storage.engine.core.context.*;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FastDFSStorageEngine extends AbstractStorageEngine {
    /**
     * 执行保存物理文件的动作
     *
     * @param context
     * @throws IOException
     */
    @Override
    protected void doStore(StoreFileContext context) throws IOException {

    }
    /**
     * 执行删除物理文件的动作
     *
     * @param context
     */
    @Override
    protected void doDelete(DeleteFileContext context) throws IOException {

    }
    /**
     * 执行保存文件分片
     *
     * @param context
     * @throws IOException
     */
    @Override
    protected void doStoreChunk(StoreFileChunkContext context) throws IOException {

    }

    /**
     * 执行文件分片的动作
     * @param context
     */
    @Override
    protected void doMergeFile(MergeFileContext context) throws IOException {

    }

    /**
     * 读取文件内容并写入到输出流中
     *
     * @param context
     */
    @Override
    protected void doReadFile(ReadFileContext context)throws IOException {

    }
}
