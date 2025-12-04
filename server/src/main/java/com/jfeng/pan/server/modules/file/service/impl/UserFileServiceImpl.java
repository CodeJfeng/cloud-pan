package com.jfeng.pan.server.modules.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.modules.file.constants.FileConstants;
import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.context.QueryFileListContext;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.file.enums.DelFlagEnum;
import com.jfeng.pan.server.modules.file.enums.FolderFlagEnum;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.mapper.RPanUserFileMapper;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
* @author 16837
* @description 针对表【r_pan_user_file(用户文件信息表)】的数据库操作Service实现
* @createDate 2025-11-06 19:22:58
*/
@Service
public class UserFileServiceImpl extends ServiceImpl<RPanUserFileMapper, RPanUserFile> implements IUserFileService {
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

    /****************************************************** private ***************************************************************/
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




