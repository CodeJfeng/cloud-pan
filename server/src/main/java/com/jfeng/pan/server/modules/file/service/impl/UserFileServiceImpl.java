package com.jfeng.pan.server.modules.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.FileUtil;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.event.file.DeleteFileEvent;
import com.jfeng.pan.server.modules.file.constants.FileConstants;
import com.jfeng.pan.server.modules.file.context.*;
import com.jfeng.pan.server.modules.file.converter.FileConverter;
import com.jfeng.pan.server.modules.file.entity.RPanFile;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.file.enums.DelFlagEnum;
import com.jfeng.pan.server.modules.file.enums.FileTypeEnum;
import com.jfeng.pan.server.modules.file.enums.FolderFlagEnum;
import com.jfeng.pan.server.modules.file.service.IFileChunkService;
import com.jfeng.pan.server.modules.file.service.IFileService;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.mapper.RPanUserFileMapper;
import com.jfeng.pan.server.modules.file.vo.FileChunkUploadVO;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
* @author 16837
* @description 针对表【r_pan_user_file(用户文件信息表)】的数据库操作Service实现
* @createDate 2025-11-06 19:22:58
*/
@Service
public class UserFileServiceImpl extends ServiceImpl<RPanUserFileMapper, RPanUserFile> implements IUserFileService, ApplicationContextAware {

    private  ApplicationContext applicationContext;

    @Autowired
    private IFileService iFileService;

    @Autowired
    private IFileChunkService iFileChunkService;

    @Autowired
    private FileConverter fileConverter;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 创建文件夹信息
     * @param createFolderContext
     * @return
     */
    @Override
    public Long createFolder(CreateFolderContext createFolderContext) {
        return saveUserFile(createFolderContext.getParentId(),
                createFolderContext.getFolderName(),
                FolderFlagEnum.YES,
                null,
                null,
                createFolderContext.getUserId(),
                null);
    }

    /**
     * 查询用户的根文件夹信息
     *
     * @param userId
     * @return
     */
    @Override
    public RPanUserFile getUserRootFile(Long userId){
        LambdaQueryWrapper<RPanUserFile> rPanUserFileLambdaQueryWrapper = new LambdaQueryWrapper<>();
        rPanUserFileLambdaQueryWrapper.eq(RPanUserFile::getUserId, userId)
                .eq(RPanUserFile::getParentId, FileConstants.TOP_PARENT_ID)
                .eq(RPanUserFile::getDelFlag, DelFlagEnum.NO.getCode())
                .eq(RPanUserFile::getFolderFlag, DelFlagEnum.YES.getCode());
        return getOne(rPanUserFileLambdaQueryWrapper);
    }

    /**
     * 查询用户的文件列表
     *
     * @param queryFileListContext
     * @return
     */
    @Override
    public List<RPanUserFileVO> getFileList(QueryFileListContext queryFileListContext) {
        return baseMapper.selectFileList(queryFileListContext);
    }

    /**
     * 更新文件名称实现类
     * 1、校验更新文件名称的条件
     * 2、执行更新文件名称的操作
     * @param context
     * @return
     */
    @Override
    public Long updateFilename(UpdateFilenameContext context) {
        checkUpdateFilenameCondition(context);
        doUpdateFilename(context);
        return context.getFileId();
    }

    /**
     * 批量删除用户文件
     * 1、校验删除的条件
     * 2、执行批量删除的动作
     * 3、发布批量删除文件的时间，给其他模块订阅使用 （事件模型）
     *
     * @param deleteFileContext
     */
    @Override
    public void deleteFile(DeleteFileContext deleteFileContext) {
        checkFileDeleteCondition(deleteFileContext);
        doDeleteFile(deleteFileContext);
        afterFileDelete(deleteFileContext);
    }

    /**
     * 文件秒传
     * 1、通过文件的唯一标识，查找对应的实体文件记录
     * 2、如果没有查到，直接返回秒传失败
     * 3、如果查到记录，直接挂载到关联关系，返回秒传成功
     *
     * @param context
     * @return
     */
    @Override
    public boolean SecUpload(SecUploadContext context) {
        RPanFile recode = getFileByUserIdAndIdentifier(context.getUserId(), context.getIdentifier());
        if(Objects.isNull(recode)){
            return false;
        }
        saveUserFile(context.getParentId(),
                context.getFilename(),
                FolderFlagEnum.NO,
                FileTypeEnum.getFileTypeCode(FileUtil.getFileSuffix(context.getFilename())),
                recode.getFileId(),
                context.getUserId(),
                recode.getFileSizeDesc());
        return true;
    }

    /**
     * 单文件上传
     * 1、上传文件并保存实体文件记录
     * 2、保存用户文件的关系记录
     *
     * @param context
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void upload(FileUploadContext context) {
        saveFile(context);
        saveUserFile(context.getParentId(),
                context.getFilename(),
                FolderFlagEnum.NO,
                FileTypeEnum.getFileTypeCode(FileUtil.getFileSuffix(context.getFilename())),
                context.getRecode().getFileId(),
                context.getUserId(),
                context.getRecode().getFileSizeDesc());
    }

    /**
     * 文件分片上传
     * 1、上传文件实体
     * 2、保存分片文件的记录
     * 3、校验是否全部的文件上传
     *
     * @param context
     * @return
     */
    @Override
    public FileChunkUploadVO chunkUpload(FileChunkUploadContext context) {
        FileChunkSaveContext fileChunkSaveContext = fileConverter.fileChunkUploadContext2FileChunkSaveContext(context);
        iFileChunkService.saveChunkFile(fileChunkSaveContext);
        FileChunkUploadVO vo = new FileChunkUploadVO();
        vo.setMergeFlag(fileChunkSaveContext.getMergeFlagEnum().getCode());
        return vo;
    }

/****************************************************** private ***************************************************************/


    /**
     * 上传文件并保存文件实体记录
     * 委托给实体文件的Service去完成该操作
     *
     * @param context
     */
    private void saveFile(FileUploadContext context) {
        FileSaveContext fileSaveContext = fileConverter.fileUploadContext2FileSaveContext(context);
        iFileService.saveFile(fileSaveContext);
        context.setRecode(fileSaveContext.getRecode());
    }
    /**
     *  根据用户id和文件唯一标识获取第一条文件信息
     *
     * @param userId
     * @param identifier
     * @return
     */
    private RPanFile getFileByUserIdAndIdentifier(Long userId, String identifier) {
        LambdaQueryChainWrapper<RPanFile> wrapper = new LambdaQueryChainWrapper<>(RPanFile.class);
        wrapper.eq(RPanFile::getCreateUser, userId)
                .eq(RPanFile::getIdentifier, identifier);
        List<RPanFile> list = wrapper.list();
        if(CollectionUtils.isEmpty(list)){
            return null;
        }
        return list.getFirst();
    }

    /**
     * 文件删除的后置操作
     * 1、对外发布文件删除的事件
     * @param deleteFileContext
     */
    private void afterFileDelete(DeleteFileContext deleteFileContext) {
        DeleteFileEvent deleteFileEvent = new DeleteFileEvent(this, deleteFileContext.getFileIdList());
        applicationContext.publishEvent(deleteFileEvent);
    }

    /**
     * 执行文件删除的操作
     * @param deleteFileContext
     */
    private void doDeleteFile(DeleteFileContext deleteFileContext) {
        List<Long> fileIdList = deleteFileContext.getFileIdList();

        LambdaUpdateWrapper<RPanUserFile> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(RPanUserFile::getFileId, fileIdList)
                .set(RPanUserFile::getDelFlag, DelFlagEnum.YES.getCode())
                .set(RPanUserFile::getUpdateTime, new Date());
        if(!update(wrapper)){
            throw new RPanBusinessException("文件删除失败");
        }

    }

    /**
     * 删除文件之前的前置校验
     * 1、文件ID合法校验
     * 2、用户拥有删除该文件的权限
     *
     * @param deleteFileContext
     */
    private void checkFileDeleteCondition(DeleteFileContext deleteFileContext) {
        List<Long> fileIdsList = deleteFileContext.getFileIdList();
        List<RPanUserFile> rPanUserFiles = listByIds(fileIdsList);
        if(rPanUserFiles.size() != fileIdsList.size())
            throw new RPanBusinessException("存在不合法的文件记录");

        Set<Long> fileIdSet = rPanUserFiles.stream().map(RPanUserFile::getFileId).collect(Collectors.toSet());
        int oldSize = fileIdSet.size();
        fileIdSet.addAll(fileIdsList);
        int newSize = fileIdSet.size();
        if(oldSize != newSize){
            throw new RPanBusinessException("存在不合法的文件信息");
        }

        Set<Long> userIdSet = rPanUserFiles.stream().map(RPanUserFile::getUserId).collect(Collectors.toSet());
        if (userIdSet.size() != 1){
            throw new RPanBusinessException("存在不合法的文件记录");
        }

        Long dbUserId = userIdSet.stream().findFirst().get();
        if(!Objects.equals(dbUserId, deleteFileContext.getUserId())){
            throw new RPanBusinessException("当前登录用户没有删除该文件的权限");
        }
    }

    /**
     * 执行文件名称更新的操作
     * @param context
     */
    private void doUpdateFilename(UpdateFilenameContext context) {
        RPanUserFile entity = context.getEntity();
        entity.setFilename(context.getNewFileName());
        entity.setUpdateUser(context.getUserId());
        entity.setUpdateTime(new Date());

        if(!updateById(entity)){
            throw new RuntimeException("文件重命名失败");
        }
    }

    /**
     * 更新文件名称的校验逻辑
     * 1、文件ID是有效的
     * 2、用户有权限更新该文件的文件名称
     * 3、新旧文件名称不能一样
     * 4、不能使用当前文件夹下面子文件夹的名称
     *
     * @param context
     */
    private void checkUpdateFilenameCondition(UpdateFilenameContext context) {
        Long fileId = context.getFileId();
        RPanUserFile entity = getById(fileId);
        if(Objects.isNull(entity)){
            throw new RPanBusinessException("该文件ID无效");
        }

        if(!Objects.equals(entity.getUserId(), context.getUserId())){
            throw new RPanBusinessException("当前用户没有修改该文件名称的权限");
        }

        if (Objects.equals(entity.getFilename(), context.getNewFileName())){
            throw new RPanBusinessException("不能与当前文件夹名称一致");
        }

        LambdaQueryWrapper<RPanUserFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RPanUserFile::getParentId, fileId)
                .eq(RPanUserFile::getFilename, context.getNewFileName());
        long count = count(wrapper);
        if(count > 0){
            throw new RPanBusinessException("当前文件名称已被使用");
        }

        context.setEntity(entity);
    }


    /**
     * 保存用户文件的映射记录
     * @param parentID
     * @param filename
     * @param folderFlagEnum
     * @param fileType
     * @param realFileId
     * @param userID
     * @param fileSizeDesc
     * @return
     */
    private Long saveUserFile(Long parentID,
                              String filename,
                              FolderFlagEnum folderFlagEnum,
                              Integer fileType,
                              Long realFileId,
                              Long userID,
                              String fileSizeDesc) {

        RPanUserFile entity = assembleRPanUserFile(parentID, userID, filename, folderFlagEnum, fileType, realFileId, fileSizeDesc);
        if(!save(entity)){
            throw new RPanBusinessException("保存文件失败");
        }
        return entity.getFileId();
    }


    /**
     * 用户文件映射实体关系实体转换
     * 1、构建并填充实体
     * 2、处理文件命名一致的问题
     *
     * @param parentID
     * @param userID
     * @param filename
     * @param folderFlagEnum
     * @param fileType
     * @param realFileId
     * @param fileSizeDesc
     * @return
     */
    private RPanUserFile assembleRPanUserFile(Long parentID, Long userID, String filename, FolderFlagEnum folderFlagEnum, Integer fileType, Long realFileId, String fileSizeDesc) {
        RPanUserFile entity = new RPanUserFile();
        entity.setFileId(IdUtil.get());
        entity.setUserId(userID);
        entity.setParentId(parentID);
        entity.setRealFileId(realFileId);
        entity.setFilename(filename);
        entity.setFolderFlag(folderFlagEnum.getCode());
        entity.setFileSizeDesc(fileSizeDesc);
        entity.setFileType(fileType);
        entity.setDelFlag(DelFlagEnum.NO.getCode());
        entity.setCreateUser(userID);
        entity.setCreateTime(new Date());
        entity.setUpdateUser(userID);
        entity.setUpdateTime(new Date());

        handleDuplicateFilename(entity);


        return entity;
    }

    /**
     * 处理用户重复命名文件
     * 如果同一文件夹下有文件名重复
     * 按照系统规则重命名文件
     *
     * @param entity
     */
    private void handleDuplicateFilename(RPanUserFile entity) {
        String filename = entity.getFilename(),
                newFilenameWithoutSuffix,
                newFileNameSuffix;
        int newFilenamePointPosition = filename.lastIndexOf(RPanConstants.POINT_STR);
        if(newFilenamePointPosition == RPanConstants.MINUS_ONE_INT){
            newFilenameWithoutSuffix = filename;
            newFileNameSuffix =RPanConstants.EMPTY_STR;
        }else{
            newFilenameWithoutSuffix = filename.substring(0, newFilenamePointPosition);
            newFileNameSuffix = filename.substring(newFilenamePointPosition);
        }
        long count = getDuplicateFilename(entity, newFilenameWithoutSuffix);
        if(count == 0){
            return;
        }
        String newFilename= assmebleNewFilename(newFilenameWithoutSuffix, count, newFileNameSuffix);
    }

    /**
     * 拼装新文件名称
     * 拼装规则根window系统一致
     * @param newFilenameWithoutSuffix
     * @param count
     * @param newFileNameSuffix
     * @return
     */
    private String assmebleNewFilename(String newFilenameWithoutSuffix, long count, String newFileNameSuffix) {
        return newFilenameWithoutSuffix +
                FileConstants.LEFT_PARENTHESIS_STR +
                count +
                FileConstants.RIGHT_PARENTHESIS_STR +
                newFileNameSuffix;
    }

    /**
     * 查找统一父文件夹下的同名文件数量
     * @param entity
     * @param newFilenameWithoutSuffix
     * @return
     */
    private long getDuplicateFilename(RPanUserFile entity, String newFilenameWithoutSuffix) {
        LambdaQueryWrapper<RPanUserFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RPanUserFile::getParentId, entity.getParentId())
                .eq(RPanUserFile::getFolderFlag, entity.getFolderFlag())
                .eq(RPanUserFile::getUserId, entity.getUserId())
                .eq(RPanUserFile::getDelFlag, DelFlagEnum.NO.getCode())
                .likeLeft(RPanUserFile::getFilename, newFilenameWithoutSuffix);
        return count(queryWrapper);
    }
}




