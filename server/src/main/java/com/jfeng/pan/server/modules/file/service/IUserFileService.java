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
         * 
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
         * 
         * @param fileUploadContext
         */
        void upload(FileUploadContext fileUploadContext);

        /**
         * 文件分片上传
         * 
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
         * 
         * @param context
         */
        void mergeFile(FileChunkMergeContext context);

        /**
         * 文件下载
         * 
         * @param context
         */
        void download(FileDownloadContext context);

        /**
         * 文件下载
         * 不校验用户是不是上传用户
         * 
         * @param context
         */
        void downloadWithoutCheckUser(FileDownloadContext context);

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
         * 
         * @param context
         */
        void transfer(TransferFileConext context);

        /**
         * 文件批量复制
         * 
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

        /**
         * 获取面包屑列表
         * 
         * @param context
         * @return
         */
        List<BreadcrumbVO> getBreadcrumbs(QueryBreadcrumbContext context);

        /**
         * 递归查询所有的子文件信息
         *
         * @param records
         * @return
         */
        List<RPanUserFile> findAllFileRecords(List<RPanUserFile> records);

        /**
         * 递归查询所有的子文件信息
         * 
         * @param fileIdList
         * @return
         */
        List<RPanUserFile> findAllFileRecordsByFileIdList(List<Long> fileIdList);

        /**
         * 实体转换
         * 
         * @param records
         * @return
         */
        List<RPanUserFileVO> transferVOList(List<RPanUserFile> records);

        /**
         * 生成单文件上传预签名URL
         * 用于客户端直传S3场景，服务端签发临时上传凭证
         *
         * @param context 预签名URL生成上下文，包含文件名、文件大小、用户ID等信息
         * @return 预签名URL响应结果，包含上传URL和objectKey
         */
        PresignedUrlVO generatePresignedUrl(
                        com.jfeng.pan.server.modules.file.context.GeneratePresignedUrlContext context);

        /**
         * 初始化分片上传并生成预签名URL
         * 用于大文件分片直传场景，返回uploadId用于后续分片上传
         *
         * @param context 分片上传初始化上下文，包含文件名、文件大小、分片总数等信息
         * @return 预签名URL响应结果，包含上传URL、objectKey和uploadId
         */
        PresignedUrlVO initMultipartUpload(
                        com.jfeng.pan.server.modules.file.context.GeneratePresignedMultipartUrlContext context);

        /**
         * 生成指定分片的预签名URL
         * 用于分片上传场景，为每个分片生成独立的上传URL
         *
         * @param context 分片上传上下文，包含objectKey、uploadId、分片号等信息
         * @return 预签名URL响应结果，包含该分片的上传URL
         */
        PresignedUrlVO generatePresignedPartUrl(
                        com.jfeng.pan.server.modules.file.context.GeneratePresignedPartUrlContext context);

        /**
         * 完成客户端直传
         * 客户端完成文件上传后，调用此接口保存文件记录
         *
         * @param context 完成直传上下文，包含objectKey、uploadId（分片上传时）、文件名、文件大小等信息
         */
        void completeDirectUpload(com.jfeng.pan.server.modules.file.context.CompleteDirectUploadContext context);

        /**
         * 查询已上传的分片列表
         * 用于断点续传场景，获取 S3 上已上传的分片编号列表
         *
         * @param context 查询已上传分片上下文，包含objectKey、uploadId等信息
         * @return 已上传分片列表响应
         */
        com.jfeng.pan.server.modules.file.vo.UploadedPartsVO listUploadedParts(
                        com.jfeng.pan.server.modules.file.context.QueryUploadedPartsContext context);
}
