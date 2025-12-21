package com.jfeng.pan.server.modules.file.controller;

import cn.hutool.core.text.StrSplitter;
import cn.hutool.core.util.StrUtil;
import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.utils.UserIdUtil;
import com.jfeng.pan.server.modules.file.constants.FileConstants;
import com.jfeng.pan.server.modules.file.context.*;
import com.jfeng.pan.server.modules.file.converter.FileConverter;
import com.jfeng.pan.server.modules.file.enums.DelFlagEnum;
import com.jfeng.pan.server.modules.file.po.*;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.vo.FileChunkUploadVO;
import com.jfeng.pan.server.modules.file.vo.FolderTreeNodeVO;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import com.jfeng.pan.server.modules.file.vo.UploadedChunksVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 文件模块控制器
 */
@RestController
@Validated
@Tag(name = "文件接口")
public class FileController {

    @Autowired
    private IUserFileService iUserFileService;

    @Autowired
    private FileConverter  fileConverter;

    @Operation(summary = "查询文件列表",
                description = "该接口提供了用户查询某文件夹下面某些文件类型的文件列表的功能")
    @GetMapping("files")
    public R<List<RPanUserFileVO>> list(@NotBlank(message = "父文件夹ID不能为空") @RequestParam (value = "parentId", required = false) String parentId,
                                        @RequestParam (value = "fileTypes", required = false, defaultValue = FileConstants.ALL_FILE_TYPE) String fileType ){
        Long realParentId = IdUtil.decrypt(parentId);
        List<Integer> fileTypeArray = null;

        if(!Objects.equals(FileConstants.ALL_FILE_TYPE, fileType)){
            fileTypeArray = StrSplitter.split(fileType, RPanConstants.COMMON_SEPARATOR, true,true).stream().map(Integer::valueOf).toList();

        }

        QueryFileListContext queryFileListContext = new QueryFileListContext();
        queryFileListContext.setParentId(realParentId);
        queryFileListContext.setFileTypeArray(fileTypeArray);
        queryFileListContext.setUserId(UserIdUtil.get());
        queryFileListContext.setDelFlag(DelFlagEnum.NO.getCode());

        List<RPanUserFileVO> result = iUserFileService.getFileList(queryFileListContext);
        return R.data(result);
    }

    @Operation(summary = "创建文件夹",
            description = "该接口提供了用户创建文件夹的功能")
    @PostMapping("file/folder")
    public R createFolder(@Validated @RequestBody CreateFolderPO createFolderPO){
        CreateFolderContext createFolderContext = fileConverter.createFolderPO2CreateFolderContext(createFolderPO);
        Long fileId = iUserFileService.createFolder(createFolderContext);
        return R.data(IdUtil.encrypt(fileId));
    }

    @Operation(summary = "文件重命名",
            description = "该接口提供了文件重命名的功能")
    @PutMapping("file")
    public R updateFilename(@Validated @RequestBody UpdateFilenamePO updateFilenamePO){
        UpdateFilenameContext updateFilenameContext = fileConverter.updateFilenamePO2UpdateFilenameContext(updateFilenamePO);
        Long fileId = iUserFileService.updateFilename(updateFilenameContext);
        return R.data(IdUtil.encrypt(fileId));
    }

    @Operation(summary = "批量删除文件",
            description = "该接口提 供了文件批量删除的功能")
    @PostMapping("file")
    public R delete(@Validated @RequestBody DeleteFilePO deleteFilePO){
        DeleteFileContext deleteFileContext = fileConverter.deleteFilePO2DeleteFileContext(deleteFilePO);
        String fileIds = deleteFilePO.getFileIds();
        List<Long> fileIdList = StrUtil.split(fileIds, RPanConstants.COMMON_SEPARATOR).stream().map(IdUtil::decrypt).toList();
        deleteFileContext.setFileIdList(fileIdList);
        iUserFileService.deleteFile(deleteFileContext);
        return R.data("文件删除成功");
    }

    @Operation(summary = "文件秒传",
            description = "该接口提供了文件秒传的功能")
    @PostMapping("file/sec-upload")
    public R secUpload(@Validated @RequestBody SecUploadPO secUploadPO){
        SecUploadContext secUploadContext = fileConverter.secUploadPO2SecUploadContext(secUploadPO);
        boolean success = iUserFileService.SecUpload(secUploadContext);
        if(success){
            return R.data("文件秒传成功");
        }
        return R.fail("文件唯一标识不存在，请手动执行上传的操作");
    }

    @Operation(summary = "单文件上传",
            description = "该接口提供了单文件上传的功能")
    @PostMapping("file/upload")
    public R upload(@Validated @RequestBody FileUploadPO fileUploadPO){
        FileUploadContext fileUploadContext = fileConverter.fileUploadPO2FileUploadContext(fileUploadPO);
        iUserFileService.upload(fileUploadContext);
        return R.data("文件上传成功");
    }

    @Operation(summary = "文件分片上传",
            description = "该接口提供了文件分片上传的功能")
    @PostMapping("file/chunk-upload")
    public R<FileChunkUploadVO> upload(@Validated FileChunkUploadPO fileChunkUploadPO){
        FileChunkUploadContext context = fileConverter.fileChunkUploadPO2FileChunkUploadContext(fileChunkUploadPO);
        FileChunkUploadVO vo =  iUserFileService.chunkUpload(context);
        return R.data(vo);
    }


    @Operation(summary = "文件分片查询",
            description = "该接口提供了查询用户已经上传的分片列表")
    @GetMapping("file/chunk-upload")
    public R<UploadedChunksVO> getUploadedChunks(@Validated QueryUploadedChunksPO queryUploadedChunksPO){
        QueryUploadedChunksContext context = fileConverter.queryUploadedChunksPO2QueryUploadedChunksContext(queryUploadedChunksPO);
        UploadedChunksVO vo =  iUserFileService.getUploadedChunks(context);
        return R.data(vo);
    }

    @Operation(summary = "文件分片合并",
            description = "该接口提供了文件分片合并的的功能")
    @GetMapping("file/merge")
    public R mergeFile(@Validated @RequestBody FileChunkMergePO fileChunkMergePO){
        FileChunkMergeContext context = fileConverter.fileChunkMergePO2FileChunkMergeContext(fileChunkMergePO);
        iUserFileService.mergeFile(context);
        return R.success();
    }

    @Operation(summary = "文件下载",
            description = "该接口提供了文件下载的的功能")
    @GetMapping("file/download")
    public void download(@NotBlank(message = "文件ID不能为空") @RequestParam(value = "fileId", required = false) String fileId, HttpServletResponse response){
        FileDownloadContext context = new FileDownloadContext();
        context.setFileId(IdUtil.decrypt(fileId));
        context.setResponse(response);
        context.setUserId(UserIdUtil.get());

        iUserFileService.download(context);
    }

    @Operation(summary = "文件预览",
            description = "该接口提供了单文件预览的的功能")
    @GetMapping("file/preview")
    public void preview(@NotBlank(message = "文件ID不能为空") @RequestParam(value = "fileId", required = false) String fileId, HttpServletResponse response){
        FilePreviewContext context = new FilePreviewContext();
        context.setFileId(IdUtil.decrypt(fileId));
        context.setResponse(response);
        context.setUserId(UserIdUtil.get());

        iUserFileService.preview(context);
    }

    @Operation(summary = "查询文件夹树",
            description = "该接口提供了高性能的文件夹查询功能的的功能，使用HashMap代替递归查询")
    @GetMapping("file/folder/tree")
    public R<List<FolderTreeNodeVO>> getFolderTree(){
        QueryFolderTreeContext context = new QueryFolderTreeContext();
        context.setUserId(UserIdUtil.get());
        List<FolderTreeNodeVO> result = iUserFileService.getFolderTree(context);
        return R.data(result);
    }


    @Operation(summary = "文件转移",
            description = "该接口提供了文件转移的功能")
    @PostMapping("file/transfer")
    public R transfer(@Validated @RequestBody TransferFilePO transferFilePO){
        String fileIds = transferFilePO.getFileIds();
        String targetParentId = transferFilePO.getTargetParentId();
        List<Long> fileIdList = Arrays.stream(fileIds.split(RPanConstants.COMMON_SEPARATOR)).map(IdUtil::decrypt).toList();
        TransferFileConext context = new TransferFileConext();
        context.setFileIdList(fileIdList);
        context.setTargetParentId(IdUtil.decrypt(targetParentId));
        context.setUserId(UserIdUtil.get());
        iUserFileService.transfer(context);
        return R.success();
    }


    @Operation(summary = "文件复制",
            description = "该文件提供了文件复制的功能")
    @PostMapping("file/copy")
    public R copy(@Validated @RequestBody CopyFilePO copyFilePO){
        String fileIds = copyFilePO.getFileIds();
        String targetParentId = copyFilePO.getTargetParentId();
        List<Long> fileIdList = Arrays.stream(fileIds.split(RPanConstants.COMMON_SEPARATOR)).map(IdUtil::decrypt).toList();

        CopyFileContext context = new CopyFileContext();
        context.setFileIdList(fileIdList);
        context.setTargetParentId(IdUtil.decrypt(targetParentId));
        context.setUserId(UserIdUtil.get());
        iUserFileService.copy(context);
        return R.success();
    }



}
