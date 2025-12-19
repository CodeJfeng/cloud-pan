package com.jfeng.pan.server.modules.file.service;

import com.jfeng.pan.server.modules.file.context.FileChunkMergeAndSaveContext;
import com.jfeng.pan.server.modules.file.context.FileSaveContext;
import com.jfeng.pan.server.modules.file.entity.RPanFile;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 16837
* @description 针对表【r_pan_file(物理文件信息表)】的数据库操作Service
* @createDate 2025-11-06 19:22:58
*/
public interface IFileService extends IService<RPanFile> {

    /**
     * 上传单文件并保存文件记录
     *
     * @param context
     */
    void saveFile(FileSaveContext context);

    /**
     * 合并物理文件并保存文件记录
     * 1、委托物理存储引擎合并文件分片
     * 2、保存物理文件记录
     *
     * @param context
     */
    void mergeFileChunkAndSaveFile(FileChunkMergeAndSaveContext context);

}
