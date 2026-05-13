package com.jfeng.pan.storage.engine.local;

import com.jfeng.pan.core.utils.FileUtil;
import com.jfeng.pan.storage.engine.core.AbstractStorageEngine;
import com.jfeng.pan.storage.engine.core.context.*;
import com.jfeng.pan.storage.engine.local.config.LocalStoreEngineConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * 本地文件存储引擎实现类
 */
@Component
public class LocalStorageEngine extends AbstractStorageEngine {

    @Autowired
    private LocalStoreEngineConfig config;

    /**
     * 执行保存物理文件的动作
     *
     * @param context
     * @throws IOException
     */
    @Override
    protected void doStore(StoreFileContext context) throws IOException {
        String basePath = config.getRootFilePath();
        String realFilePath = FileUtil.generateStoreFileRealPath(basePath, context.getFilename());
        FileUtil.writeStream2File(context.getInputStream(), new File(realFilePath), context.getTotalSize());
        context.setRealPath(realFilePath);
    }

    /**
     * 执行物理删除文件的动作
     *
     * @param context
     * @throws IOException
     */
    @Override
    protected void doDelete(DeleteFileContext context) throws IOException {
        FileUtil.deleteFiles(context.getRealPathList());
    }

    /**
     * 存储物理文件的分片
     * 1、参数校验
     *
     * @param context
     * @throws IOException
     */
    @Override
    protected void doStoreChunk(StoreFileChunkContext context) throws IOException {
        String basePath = config.getRootFileChunkPath();
        String realFilePath = FileUtil.generateStoreFileChunkRealPath(basePath, context.getFilename(), context.getIdentifier(), context.getChunkNumber());
        FileUtil.writeStream2File(context.getInputStream(), new File(realFilePath), context.getTotalSize());
        context.setRealPath(realFilePath);
    }

    /**
     * 执行文件分片的动作
     * @param context
     */
    @Override
    protected void doMergeFile(MergeFileContext context) throws IOException{
        String basePath = config.getRootFilePath();
        String realFilePath = FileUtil.generateStoreFileRealPath(basePath, context.getFilename());
        FileUtil.createFile(new File(realFilePath));
        List<String > realPathList = context.getRealPathList();
        for(String chunkPath : realPathList){
            FileUtil.appendWrite(Paths.get(realFilePath), new File(chunkPath).toPath());
        }
        FileUtil.deleteFiles(realPathList);
        context.setRealPath(realFilePath);
    }

    /**
     * 读取文件内容并写入到输出流中
     *
     * @param context
     */
    @Override
    protected void doReadFile(ReadFileContext context) throws IOException {
        File file = new File(context.getRealPath());
        FileUtil.writeFile2OutputStream(new FileInputStream(file), context.getOutputStream(), file.length());
    }

    @Override
    protected String doGeneratePresignedUploadUrl(GeneratePresignedUrlContext context) {
        throw new UnsupportedOperationException("本地存储引擎不支持预签名URL");
    }

    @Override
    protected String doGeneratePresignedMultipartInitUrl(GeneratePresignedMultipartUrlContext context) {
        throw new UnsupportedOperationException("本地存储引擎不支持预签名URL");
    }

    @Override
    protected String doGeneratePresignedPartUploadUrl(GeneratePresignedPartUrlContext context) {
        throw new UnsupportedOperationException("本地存储引擎不支持预签名URL");
    }

    @Override
    protected void doCompleteMultipartUpload(CompleteMultipartUploadContext context) throws IOException {
        throw new UnsupportedOperationException("本地存储引擎不支持预签名URL");
    }
}
