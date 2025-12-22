package com.jfeng.pan.server.modules.file.service;

import com.jfeng.pan.server.modules.file.context.*;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jfeng.pan.server.modules.file.vo.*;

import java.util.List;

/**
* @author 16837
* @description 针对表【r_pan_user_file(用户文件信息表)】的数据库操作Service
* @createDate 2025-11-06 19:22:58
*/
public interface IUserFileService extends IService<RPanUserFile> {
    /**
     * 创建文件夹信息
     * @param context
     * @return
     */
    Long createFolder(CreateFolderContext context);

    /**
     * 查询用户的根文件夹信息
     *
     * @param userId
     * @return
     */
    RPanUserFile getUserRootFile(Long userId);

    /**
     * 查询用户的文件列表
     *
     * @param queryFileListContext
     * @return
     */
    List<RPanUserFileVO> getFileList(QueryFileListContext queryFileListContext);

    /**
     * 更新文件名称
     *
     * @param updateFilenameContext
     * @return
     */
    Long updateFilename(UpdateFilenameContext updateFilenameContext);

    /**
     * 批量删除用户文件
     *
     * @param deleteFileContext
     */
    void deleteFile(DeleteFileContext deleteFileContext);

    /**
     * 文件秒传
     *
     * @param secUploadContext
     * @return
     */
    boolean SecUpload(SecUploadContext secUploadContext);

    /**
     * 单文件上传
     * @param fileUploadContext
     */
    void upload(FileUploadContext fileUploadContext);

    /**
     * 文件分片上传
     * @param context
     * @return
     */
    FileChunkUploadVO chunkUpload(FileChunkUploadContext context);

    /**
     * 查询用户已经上传的分片列表
     *
     * @param context
     * @return
     */
    UploadedChunksVO getUploadedChunks(QueryUploadedChunksContext context);

    /**
     * 文件分片合并
     * @param context
     */
    void mergeFile(FileChunkMergeContext context);

    /**
     * 文件下载
     * @param context
     */
    void download(FileDownloadContext context);

    /**
     * 文件预览
     *
     * @param context
     */
    void preview(FilePreviewContext context);

    /**
     * 查询用户的文件夹树
     *
     * @param context
     * @return
     */
    List<FolderTreeNodeVO> getFolderTree(QueryFolderTreeContext context);

    /**
     * 文件转移
     * @param context
     */
    void transfer(TransferFileConext context);

    /**
     * 文件批量复制
     * @param context
     */
    void copy(CopyFileContext context);

    /**
     * 文件列表搜索
     *
     * @param context
     * @return
     */
    List<FileSearchResultVO> search(FileSearchContext context);
}
