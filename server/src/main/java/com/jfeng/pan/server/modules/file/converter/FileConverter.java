package com.jfeng.pan.server.modules.file.converter;

import com.jfeng.pan.server.modules.file.context.*;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.file.po.*;
import com.jfeng.pan.server.modules.file.vo.FolderTreeNodeVO;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import com.jfeng.pan.storage.engine.core.context.StoreFileChunkContext;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FileConverter {

    @Mapping(target = "parentId", expression = "java(com.jfeng.pan.core.utils.IdUtil.decrypt(createFolderPO.getParentId()))")
    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    CreateFolderContext createFolderPO2CreateFolderContext(CreateFolderPO createFolderPO);

    @Mapping(target = "fileId", expression = "java(com.jfeng.pan.core.utils.IdUtil.decrypt(updateFilenamePO.getFileId()))")
    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    @Mapping(target = "newFilename", source = "updateFilenamePO.newFilename")
    UpdateFilenameContext updateFilenamePO2UpdateFilenameContext(UpdateFilenamePO updateFilenamePO);

    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    DeleteFileContext deleteFilePO2DeleteFileContext(DeleteFilePO deleteFilePO);

    @Mapping(target = "parentId", expression = "java(com.jfeng.pan.core.utils.IdUtil.decrypt(secUploadPO.getParentId()))")
    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    SecUploadContext secUploadPO2SecUploadContext(SecUploadPO secUploadPO);

    @Mapping(target = "parentId", expression = "java(com.jfeng.pan.core.utils.IdUtil.decrypt(fileUploadPO.getParentId()))")
    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    FileUploadContext fileUploadPO2FileUploadContext(FileUploadPO fileUploadPO);

    @Mapping(target = "recode", ignore = true)
    FileSaveContext fileUploadContext2FileSaveContext(FileUploadContext context);

    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    FileChunkUploadContext fileChunkUploadPO2FileChunkUploadContext(FileChunkUploadPO fileChunkUploadPO);

    FileChunkSaveContext fileChunkUploadContext2FileChunkSaveContext(FileChunkUploadContext context);

    @Mapping(target = "realPath", ignore = true)
    StoreFileChunkContext fileChunkSaveContext2StoreFileChunkContext(FileChunkSaveContext fileChunkSaveContext);

    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    QueryUploadedChunksContext queryUploadedChunksPO2QueryUploadedChunksContext(
            QueryUploadedChunksPO queryUploadedChunksPO);

    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    @Mapping(target = "parentId", expression = "java(com.jfeng.pan.core.utils.IdUtil.decrypt(fileChunkMergePO.getParentId()))")
    FileChunkMergeContext fileChunkMergePO2FileChunkMergeContext(FileChunkMergePO fileChunkMergePO);

    FileChunkMergeAndSaveContext fileChunkMergeContext2FileChunkMergeAndSaveContext(FileChunkMergeContext context);

    @Mapping(target = "label", source = "record.filename")
    @Mapping(target = "id", source = "record.fileId")
    @Mapping(target = "children", expression = "java(org.assertj.core.util.Lists.newArrayList())")
    FolderTreeNodeVO rPanUserFile2FolderTreeNodeVO(RPanUserFile record);

    RPanUserFileVO rPanUserFile2RPanUserFileVO2(RPanUserFile record);

    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    GeneratePresignedUrlContext generatePresignedUrlPO2Context(GeneratePresignedUrlPO po);

    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    GeneratePresignedMultipartUrlContext initMultipartUploadPO2Context(InitMultipartUploadPO po);

    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    GeneratePresignedPartUrlContext generatePresignedPartUrlPO2Context(GeneratePresignedPartUrlPO po);

    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    @Mapping(target = "parentId", expression = "java(com.jfeng.pan.core.utils.IdUtil.decrypt(po.getParentId()))")
    CompleteDirectUploadContext completeDirectUploadPO2Context(CompleteDirectUploadPO po);
}
