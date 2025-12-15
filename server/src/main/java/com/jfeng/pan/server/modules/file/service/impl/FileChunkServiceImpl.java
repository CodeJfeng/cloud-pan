package com.jfeng.pan.server.modules.file.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.server.modules.file.context.FileChunkSaveContext;
import com.jfeng.pan.server.modules.file.entity.RPanFileChunk;
import com.jfeng.pan.server.modules.file.service.IFileChunkService;
import com.jfeng.pan.server.modules.file.mapper.RPanFileChunkMapper;
import org.springframework.stereotype.Service;

/**
* @author 16837
* @description 针对表【r_pan_file_chunk(文件分片信息表)】的数据库操作Service实现
* @createDate 2025-11-06 19:22:58
*/
@Service
public class FileChunkServiceImpl extends ServiceImpl<RPanFileChunkMapper, RPanFileChunk> implements IFileChunkService {
    /**
     * 文件分片保存
     * 1、保存文件分片和记录
     * 2、判断文件分片是否全部完成上传
     *
     * @param context
     */
    @Override
    public synchronized void saveChunkFile(FileChunkSaveContext context) {
        doSaveChunkFile(context);
        doJudgeMergeFile(context);
    }

    /**
     * 判断是否所有的分片均上传完成
     * @param context
     */
    private void doJudgeMergeFile(FileChunkSaveContext context) {


    }

    /**
     * 执行文件分片保存的操作
     * 1、委托文件存储引擎存储文件分片
     * 2、保存文件分片记录
     * @param context
     */
    private void doSaveChunkFile(FileChunkSaveContext context) {
        doStoreFileChunk(context);
        doSaveRecord(context);
    }

    private void doSaveRecord(FileChunkSaveContext context) {

    }

    /**
     * 委托文件存储引擎存储文件分片
     * @param context
     */
    private void doStoreFileChunk(FileChunkSaveContext context) {

    }
}




