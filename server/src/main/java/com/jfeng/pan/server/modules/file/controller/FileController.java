package com.jfeng.pan.server.modules.file.controller;

import cn.hutool.core.text.StrSplitter;
import cn.hutool.core.util.StrUtil;
import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.utils.UserIdUtil;
import com.jfeng.pan.server.modules.file.constants.FileConstants;
import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.context.DeleteFileContext;
import com.jfeng.pan.server.modules.file.context.QueryFileListContext;
import com.jfeng.pan.server.modules.file.context.UpdateFilenameContext;
import com.jfeng.pan.server.modules.file.converter.FileConverter;
import com.jfeng.pan.server.modules.file.enums.DelFlagEnum;
import com.jfeng.pan.server.modules.file.po.CreateFolderPO;
import com.jfeng.pan.server.modules.file.po.DeleteFilePO;
import com.jfeng.pan.server.modules.file.po.UpdateFilenamePO;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
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
    @DeleteMapping("file")
    public R delete(@Validated @RequestBody DeleteFilePO deleteFilePO){
        DeleteFileContext deleteFileContext = fileConverter.deleteFilePO2DeleteFileContext(deleteFilePO);
        String fileIds = deleteFilePO.getFileIds();
        List<Long> fileIdList = StrUtil.split(fileIds, RPanConstants.COMMON_SEPARATOR).stream().map(IdUtil::decrypt).toList();
        deleteFileContext.setFileIdList(fileIdList);
        iUserFileService.deleteFile(deleteFileContext);
        return R.data("文件删除成功");
    }




}
