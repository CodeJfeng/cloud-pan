package com.jfeng.pan.server.modules.file.converter;

import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.context.DeleteFileContext;
import com.jfeng.pan.server.modules.file.context.FileChunkMergeAndSaveContext;
import com.jfeng.pan.server.modules.file.context.FileChunkMergeContext;
import com.jfeng.pan.server.modules.file.context.FileChunkSaveContext;
import com.jfeng.pan.server.modules.file.context.FileChunkUploadContext;
import com.jfeng.pan.server.modules.file.context.FileSaveContext;
import com.jfeng.pan.server.modules.file.context.FileUploadContext;
import com.jfeng.pan.server.modules.file.context.QueryUploadedChunksContext;
import com.jfeng.pan.server.modules.file.context.SecUploadContext;
import com.jfeng.pan.server.modules.file.context.UpdateFilenameContext;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.file.po.CreateFolderPO;
import com.jfeng.pan.server.modules.file.po.DeleteFilePO;
import com.jfeng.pan.server.modules.file.po.FileChunkMergePO;
import com.jfeng.pan.server.modules.file.po.FileChunkUploadPO;
import com.jfeng.pan.server.modules.file.po.FileUploadPO;
import com.jfeng.pan.server.modules.file.po.QueryUploadedChunksPO;
import com.jfeng.pan.server.modules.file.po.SecUploadPO;
import com.jfeng.pan.server.modules.file.po.UpdateFilenamePO;
import com.jfeng.pan.server.modules.file.vo.FolderTreeNodeVO;
import com.jfeng.pan.storage.engine.core.context.StoreFileChunkContext;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-12-27T16:10:14+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.9 (Microsoft)"
)
@Component
public class FileConverterImpl implements FileConverter {

    @Override
    public CreateFolderContext createFolderPO2CreateFolderContext(CreateFolderPO createFolderPO) {
        if ( createFolderPO == null ) {
            return null;
        }

        CreateFolderContext createFolderContext = new CreateFolderContext();

        createFolderContext.setParentId( com.jfeng.pan.core.utils.IdUtil.decrypt(createFolderPO.getParentId()) );
        createFolderContext.setUserId( com.jfeng.pan.server.common.utils.UserIdUtil.get() );

        return createFolderContext;
    }

    @Override
    public UpdateFilenameContext updateFilenamePO2UpdateFilenameContext(UpdateFilenamePO updateFilenamePO) {
        if ( updateFilenamePO == null ) {
            return null;
        }

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();

        updateFilenameContext.setNewFileName( updateFilenamePO.getNewFileName() );

        updateFilenameContext.setFileId( com.jfeng.pan.core.utils.IdUtil.decrypt(updateFilenamePO.getFileId()) );
        updateFilenameContext.setUserId( com.jfeng.pan.server.common.utils.UserIdUtil.get() );

        return updateFilenameContext;
    }

    @Override
    public DeleteFileContext deleteFilePO2DeleteFileContext(DeleteFilePO deleteFilePO) {
        if ( deleteFilePO == null ) {
            return null;
        }

        DeleteFileContext deleteFileContext = new DeleteFileContext();

        deleteFileContext.setUserId( com.jfeng.pan.server.common.utils.UserIdUtil.get() );

        return deleteFileContext;
    }

    @Override
    public SecUploadContext secUploadPO2SecUploadContext(SecUploadPO secUploadPO) {
        if ( secUploadPO == null ) {
            return null;
        }

        SecUploadContext secUploadContext = new SecUploadContext();

        secUploadContext.setFilename( secUploadPO.getFilename() );
        secUploadContext.setIdentifier( secUploadPO.getIdentifier() );

        secUploadContext.setParentId( com.jfeng.pan.core.utils.IdUtil.decrypt(secUploadPO.getParentId()) );
        secUploadContext.setUserId( com.jfeng.pan.server.common.utils.UserIdUtil.get() );

        return secUploadContext;
    }

    @Override
    public FileUploadContext fileUploadPO2FileUploadContext(FileUploadPO fileUploadPO) {
        if ( fileUploadPO == null ) {
            return null;
        }

        FileUploadContext fileUploadContext = new FileUploadContext();

        fileUploadContext.setFilename( fileUploadPO.getFilename() );
        fileUploadContext.setIdentifier( fileUploadPO.getIdentifier() );
        fileUploadContext.setTotalSize( fileUploadPO.getTotalSize() );
        fileUploadContext.setFile( fileUploadPO.getFile() );

        fileUploadContext.setParentId( com.jfeng.pan.core.utils.IdUtil.decrypt(fileUploadPO.getParentId()) );
        fileUploadContext.setUserId( com.jfeng.pan.server.common.utils.UserIdUtil.get() );

        return fileUploadContext;
    }

    @Override
    public FileSaveContext fileUploadContext2FileSaveContext(FileUploadContext context) {
        if ( context == null ) {
            return null;
        }

        FileSaveContext fileSaveContext = new FileSaveContext();

        fileSaveContext.setFilename( context.getFilename() );
        fileSaveContext.setIdentifier( context.getIdentifier() );
        fileSaveContext.setTotalSize( context.getTotalSize() );
        fileSaveContext.setFile( context.getFile() );
        fileSaveContext.setUserId( context.getUserId() );

        return fileSaveContext;
    }

    @Override
    public FileChunkUploadContext fileChunkUploadPO2FileChunkUploadContext(FileChunkUploadPO fileChunkUploadPO) {
        if ( fileChunkUploadPO == null ) {
            return null;
        }

        FileChunkUploadContext fileChunkUploadContext = new FileChunkUploadContext();

        fileChunkUploadContext.setFilename( fileChunkUploadPO.getFilename() );
        fileChunkUploadContext.setIdentifier( fileChunkUploadPO.getIdentifier() );
        fileChunkUploadContext.setTotalChunks( fileChunkUploadPO.getTotalChunks() );
        fileChunkUploadContext.setChunkNumber( fileChunkUploadPO.getChunkNumber() );
        fileChunkUploadContext.setCurrentChunkSize( fileChunkUploadPO.getCurrentChunkSize() );
        fileChunkUploadContext.setTotalSize( fileChunkUploadPO.getTotalSize() );
        fileChunkUploadContext.setFile( fileChunkUploadPO.getFile() );

        fileChunkUploadContext.setUserId( com.jfeng.pan.server.common.utils.UserIdUtil.get() );

        return fileChunkUploadContext;
    }

    @Override
    public FileChunkSaveContext fileChunkUploadContext2FileChunkSaveContext(FileChunkUploadContext context) {
        if ( context == null ) {
            return null;
        }

        FileChunkSaveContext fileChunkSaveContext = new FileChunkSaveContext();

        fileChunkSaveContext.setFilename( context.getFilename() );
        fileChunkSaveContext.setIdentifier( context.getIdentifier() );
        fileChunkSaveContext.setTotalChunks( context.getTotalChunks() );
        fileChunkSaveContext.setChunkNumber( context.getChunkNumber() );
        fileChunkSaveContext.setCurrentChunkSize( context.getCurrentChunkSize() );
        fileChunkSaveContext.setTotalSize( context.getTotalSize() );
        fileChunkSaveContext.setFile( context.getFile() );
        fileChunkSaveContext.setUserId( context.getUserId() );

        return fileChunkSaveContext;
    }

    @Override
    public StoreFileChunkContext fileChunkSaveContext2StoreFileChunkContext(FileChunkSaveContext fileChunkSaveContext) {
        if ( fileChunkSaveContext == null ) {
            return null;
        }

        StoreFileChunkContext storeFileChunkContext = new StoreFileChunkContext();

        storeFileChunkContext.setFilename( fileChunkSaveContext.getFilename() );
        storeFileChunkContext.setIdentifier( fileChunkSaveContext.getIdentifier() );
        storeFileChunkContext.setTotalSize( fileChunkSaveContext.getTotalSize() );
        storeFileChunkContext.setTotalChunks( fileChunkSaveContext.getTotalChunks() );
        storeFileChunkContext.setChunkNumber( fileChunkSaveContext.getChunkNumber() );
        storeFileChunkContext.setCurrentChunkSize( fileChunkSaveContext.getCurrentChunkSize() );
        storeFileChunkContext.setUserId( fileChunkSaveContext.getUserId() );

        return storeFileChunkContext;
    }

    @Override
    public QueryUploadedChunksContext queryUploadedChunksPO2QueryUploadedChunksContext(QueryUploadedChunksPO queryUploadedChunksPO) {
        if ( queryUploadedChunksPO == null ) {
            return null;
        }

        QueryUploadedChunksContext queryUploadedChunksContext = new QueryUploadedChunksContext();

        queryUploadedChunksContext.setIdentifier( queryUploadedChunksPO.getIdentifier() );

        queryUploadedChunksContext.setUserId( com.jfeng.pan.server.common.utils.UserIdUtil.get() );

        return queryUploadedChunksContext;
    }

    @Override
    public FileChunkMergeContext fileChunkMergePO2FileChunkMergeContext(FileChunkMergePO fileChunkMergePO) {
        if ( fileChunkMergePO == null ) {
            return null;
        }

        FileChunkMergeContext fileChunkMergeContext = new FileChunkMergeContext();

        fileChunkMergeContext.setFilename( fileChunkMergePO.getFilename() );
        fileChunkMergeContext.setIdentifier( fileChunkMergePO.getIdentifier() );
        fileChunkMergeContext.setTotalSize( fileChunkMergePO.getTotalSize() );

        fileChunkMergeContext.setUserId( com.jfeng.pan.server.common.utils.UserIdUtil.get() );
        fileChunkMergeContext.setParentId( com.jfeng.pan.core.utils.IdUtil.decrypt(fileChunkMergePO.getParentId()) );

        return fileChunkMergeContext;
    }

    @Override
    public FileChunkMergeAndSaveContext fileChunkMergeContext2FileChunkMergeAndSaveContext(FileChunkMergeContext context) {
        if ( context == null ) {
            return null;
        }

        FileChunkMergeAndSaveContext fileChunkMergeAndSaveContext = new FileChunkMergeAndSaveContext();

        fileChunkMergeAndSaveContext.setFilename( context.getFilename() );
        fileChunkMergeAndSaveContext.setIdentifier( context.getIdentifier() );
        fileChunkMergeAndSaveContext.setTotalSize( context.getTotalSize() );
        fileChunkMergeAndSaveContext.setParentId( context.getParentId() );
        fileChunkMergeAndSaveContext.setUserId( context.getUserId() );
        fileChunkMergeAndSaveContext.setRecord( context.getRecord() );

        return fileChunkMergeAndSaveContext;
    }

    @Override
    public FolderTreeNodeVO rPanUserFile2FolderTreeNodeVO(RPanUserFile record) {
        if ( record == null ) {
            return null;
        }

        FolderTreeNodeVO folderTreeNodeVO = new FolderTreeNodeVO();

        folderTreeNodeVO.setLabel( record.getFilename() );
        folderTreeNodeVO.setId( record.getFileId() );
        folderTreeNodeVO.setParentId( record.getParentId() );

        folderTreeNodeVO.setChildren( org.assertj.core.util.Lists.newArrayList() );

        return folderTreeNodeVO;
    }
}
