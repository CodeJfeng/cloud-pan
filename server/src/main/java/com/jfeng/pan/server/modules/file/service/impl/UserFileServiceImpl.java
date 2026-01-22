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
import com.jfeng.pan.server.common.event.search.UserSearchEvent;
import com.jfeng.pan.server.common.utils.HttpUtil;
import com.jfeng.pan.server.modules.file.constants.FileConstants;
import com.jfeng.pan.server.modules.file.context.*;
import com.jfeng.pan.server.modules.file.converter.FileConverter;
import com.jfeng.pan.server.modules.file.entity.RPanFile;
import com.jfeng.pan.server.modules.file.entity.RPanFileChunk;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.file.enums.DelFlagEnum;
import com.jfeng.pan.server.modules.file.enums.FileTypeEnum;
import com.jfeng.pan.server.modules.file.enums.FolderFlagEnum;
import com.jfeng.pan.server.modules.file.service.IFileChunkService;
import com.jfeng.pan.server.modules.file.service.IFileService;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.mapper.RPanUserFileMapper;
import com.jfeng.pan.server.modules.file.vo.*;
import com.jfeng.pan.storage.engine.core.StorageEngine;
import com.jfeng.pan.storage.engine.core.context.ReadFileContext;
import org.assertj.core.util.Lists;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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

    /**
     * 查询用户已经上传的分片列表
     * 1、查询已上传对的分片列表
     * 2、封装返回实体
     *
     * @param context
     * @return
     */
    @Override
    public UploadedChunksVO getUploadedChunks(QueryUploadedChunksContext context) {
        LambdaQueryWrapper<RPanFileChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(RPanFileChunk::getChunkNumber);
        wrapper.eq(RPanFileChunk::getIdentifier, context.getIdentifier());
        wrapper.eq(RPanFileChunk::getCreateUser, context.getUserId());
        wrapper.gt(RPanFileChunk::getExpirationTime, new Date());

        List<Integer> uploadedChunks = iFileChunkService.listObjs(wrapper, value -> (Integer) value);

        UploadedChunksVO vo = new UploadedChunksVO();
        vo.setUploadedChunks(uploadedChunks);
        return vo;
    }

    /**
     * 文件分片合并
     * 1、文件实体物理合并
     * 2、保存文件实体记录
     * 3、保存文件实体用户映射
     *
     * @param context
     */
    @Override
    public void mergeFile(FileChunkMergeContext context) {
        mergeFileChunkAndSaveFile(context);
        saveUserFile(context.getParentId(),
                context.getFilename(),
                FolderFlagEnum.NO,
                FileTypeEnum.getFileTypeCode(FileUtil.getFileSuffix(context.getFilename())),
                context.getRecord().getFileId(),
                context.getUserId(),
                context.getRecord().getFileSizeDesc()
        );
    }

    /**
     * 文件下载
     *  1、 参数校验：检验文件是否存在，文件是否属于该用户，
     *  2、该文件是不是文件
     *  3、执行下载的动作
     * @param context
     */
    @Override
    public void download(FileDownloadContext context) {
        RPanUserFile record = getById(context.getFileId());
        checkOperatePermission(record, context.getUserId());
        if(checkIsFolder(record)){
            throw new RPanBusinessException("文件夹暂时不支持下载");
        }
        doDownload(record, context.getResponse());
    }

    /**
     * 文件下载
     * 不校验用户是不是上传用户
     *
     * @param context
     */
    @Override
    public void downloadWithoutCheckUser(FileDownloadContext context) {
        RPanUserFile record = getById(context.getFileId());
        if(Objects.isNull(record)){
            throw new RPanBusinessException("当前文件记录不存在");
        }
        if(checkIsFolder(record)){
            throw new RPanBusinessException("文件夹暂时不支持下载");
        }
        doDownload(record, context.getResponse());
    }

    /**
     * 文件预览
     * 1、参数校验
     * 2、该文件是不是文件
     * 3、执行文件预览的动作
     * @param context
     */
    @Override
    public void preview(FilePreviewContext context) {
        RPanUserFile record = getById(context.getFileId());
        checkOperatePermission(record, context.getUserId());
        if(checkIsFolder(record)){
            throw new RPanBusinessException("文件夹暂时不支持预览");
        }
        doPreview(record, context.getResponse());

    }

    /**
     * 查询用户的文件夹树
     * 1、查询出该用户所有的文件夹列表
     * 2、在内存中拼装文件夹树
     *
     * @param context
     * @return
     */
    @Override
    public List<FolderTreeNodeVO> getFolderTree(QueryFolderTreeContext context) {
        List<RPanUserFile> folderRecords = queryFolderRecords(context.getUserId());
        return assembleFolderTreeNodeVOList(folderRecords);
    }

    /**
     * 文件转移
     * 1、权限校验
     * 2、执行动作
     * @param context
     */
    @Override
    public void transfer(TransferFileConext context) {
        checkTransferCondition(context);
        doTransfer(context);
    }

    /**
     * 文件批量复制
     * 1、条件校验
     * 2、执行动作
     *
     * @param context
     */
    @Override
    public void copy(CopyFileContext context) {
        checkCopyCondition(context);
        doCopyFile(context);
    }

    /**
     * 执行文件复制的动作
     *
     * @param context
     */
    private void doCopyFile(CopyFileContext context) {
        List<RPanUserFile> prepareRecords = context.getPrepareRecords();
        if(!CollectionUtils.isEmpty(prepareRecords)){
           List<RPanUserFile> allRecord = Lists.newArrayList();
           prepareRecords.forEach(record -> assembleCopyChildRecord(allRecord, record, context.getTargetParentId(), context.getUserId()));
        }
    }

    /**
     * 文件列表搜索
     * 1、执行文件搜索
     * 2、拼装文件的父文件ID
     * 3、执行文件搜索后的后置动作
     * 4、
     * @param context
     * @return
     */
    @Override
    public List<FileSearchResultVO> search(FileSearchContext context) {
        List<FileSearchResultVO> result = doSearch(context);
        fileParentFilename(result);
        afterSearch(context);
        return result;
    }

    /**
     * 获取面包屑列表
     * 1、获取用户所有的文件夹信息
     * 2、拼接需要用到的面包屑列表
     *
     * @param context
     * @return
     */
    @Override
    public List<BreadcrumbVO> getBreadcrumbs(QueryBreadcrumbContext context) {
        List<RPanUserFile> folderRecords = queryFolderRecords(context.getUserId());
        Map<Long, BreadcrumbVO> prepareBreadcrumbVOMap = folderRecords.stream().map(BreadcrumbVO::transfer).collect(Collectors.toMap(BreadcrumbVO::getId, a -> a));

        BreadcrumbVO currentNode;
        Long fileId = context.getFileId();

        List<BreadcrumbVO> result = new LinkedList<>();
        do {
            currentNode = prepareBreadcrumbVOMap.get(fileId);
            if(Objects.nonNull(currentNode)){
                result.addFirst(currentNode);
                fileId = currentNode.getParentId();
            }
        }while (Objects.nonNull(currentNode));

        return result;
    }

    /**
     * 递归查询所有的子文件信息
     *
     * @param records
     * @return
     */
    @Override
    public List<RPanUserFile> findAllFileRecords(List<RPanUserFile> records) {
        List<RPanUserFile> result = Lists.newArrayList(records);
        if(CollectionUtils.isEmpty(result)){
            return result;
        }

        long folderCount = result.stream().filter(record -> Objects.equals(record.getFolderFlag(), FolderFlagEnum.YES.getCode())).count();
        if (folderCount == 0){
            return records;
        }
        records.forEach(record -> doFindAllChildRecords(result, record));
        return result;
    }

    /**
     * 递归查询所有的子文件信息
     *
     * @param fileIdList
     * @return
     */
    @Override
    public List<RPanUserFile> findAllFileRecordsByFileIdList(List<Long> fileIdList) {
        if (CollectionUtils.isEmpty(fileIdList)){
            return Lists.newArrayList();
        }
        List<RPanUserFile> records = listByIds(fileIdList);
        if (CollectionUtils.isEmpty(records)){
            return Lists.newArrayList();
        }
        return findAllFileRecords(records);
    }

    /**
     * 实体转换
     *
     * @param records
     * @return
     */
    @Override
    public List<RPanUserFileVO> transferVOList(List<RPanUserFile> records) {
        if (CollectionUtils.isEmpty(records)){
            return Lists.newArrayList();
        }
        return records.stream().map(fileConverter::rPanUserFile2RPanUserFileVO2).toList();
    }

/****************************************************** private ***************************************************************/

    /**
     * 递归查询所有的子文件列表
     * 忽略是否删除的标识
     * @param result
     * @param record
     */
    private void doFindAllChildRecords(List<RPanUserFile> result, RPanUserFile record) {
        if(Objects.isNull(record)){
            return;
        }
        if(!checkIsFolder(record)){
            return;
        }
        List<RPanUserFile> childRecords = findChildRecordsIgnoreDelFlag(record.getFileId());
        if (CollectionUtils.isEmpty(childRecords)){
            return;
        }
        result.addAll(childRecords);
        childRecords.stream()
                .filter(childRecord -> FolderFlagEnum.YES.getCode().equals(childRecord.getFolderFlag()))
                .forEach(
                        childRecord -> doFindAllChildRecords(result, childRecord)
                );
    }

    /**
     * 查询文件夹下面的文件记录，忽略删除标识
     *
     * @param fileId
     * @return
     */
    private List<RPanUserFile> findChildRecordsIgnoreDelFlag(Long fileId) {
        LambdaQueryWrapper<RPanUserFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RPanUserFile::getParentId, fileId);
        return list(wrapper);
    }

    /**
     * 搜索的后置操作
     * 1、发布文件搜索的事件--搜索历史的保存
     *
     * @param context
     */
    private void afterSearch(FileSearchContext context) {
        UserSearchEvent event = new UserSearchEvent(this, context.getKeyword(), context.getUserId() );
        applicationContext.publishEvent(event);

    }

    /**
     * 填充文件列表的父文件夹名称
     *
     * @param result
     */
    private void fileParentFilename(List<FileSearchResultVO> result) {
        if(CollectionUtils.isEmpty(result)){
            return;
        }
        List<Long> list = result.stream().map(FileSearchResultVO::getParentId).toList();
        List<RPanUserFile> parentRecords = listByIds(list);
        Map<Long, String> fileId2FileNameMap = parentRecords.stream().collect(Collectors.toMap(RPanUserFile::getFileId, RPanUserFile::getFilename));
        result.forEach(vo -> vo.setParentFilename(fileId2FileNameMap.get(vo.getParentId())));
    }

    /**
     * 搜索文件列表
     *
     * @param context
     * @return
     */
    private List<FileSearchResultVO> doSearch(FileSearchContext context) {
        return baseMapper.searchFile(context);
    }

    /**
     * 拼装所有当前文件记录以及所有子文件记录
     *
     * @param allRecord
     * @param record
     * @param targetParentId
     * @param userId
     */
    private void assembleCopyChildRecord(List<RPanUserFile> allRecord, RPanUserFile record, Long targetParentId, Long userId) {
        Long newFileId = IdUtil.get();
        Long oldFileId = record.getFileId();

        record.setFileId(newFileId);
        record.setParentId(targetParentId);
        record.setUserId(userId);
        record.setCreateUser(userId);
        record.setCreateTime(new Date());
        record.setUpdateUser(userId);
        record.setUpdateTime(new Date());

        allRecord.add(record);
        if(checkIsFolder(record)){
            List<RPanUserFile> childRecords = findChildRecords(oldFileId);
            if(CollectionUtils.isEmpty(childRecords)){
                return;
            }
            childRecords.forEach(childRecord -> assembleCopyChildRecord(allRecord, childRecord, targetParentId, userId));
        }
    }

    /**
     *  查找下一级的文件记录
     *
     * @param parentId
     * @return
     */
    private List<RPanUserFile> findChildRecords(Long parentId) {
        LambdaQueryWrapper<RPanUserFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RPanUserFile::getParentId, parentId);
        wrapper.eq(RPanUserFile::getDelFlag, DelFlagEnum.NO.getCode());
        return list(wrapper);
    }

    /**
     * 文件复制的条件校验
     * 1、目标文件ID必须是一个文件夹
     * 2、选中的文件Id列表中不能含有目标文件夹以及其子文件夹
     *
     * @param context
     */
    private void checkCopyCondition(CopyFileContext context) {
        Long targetParentId = context.getTargetParentId();
        if(!checkIsFolder(getById(targetParentId))){
            throw new RPanBusinessException("目标文件不是一个文件夹");
        }

        List<Long> fileIdList = context.getFileIdList();
        List<RPanUserFile> prepareRecords = listByIds(fileIdList);
        context.setPrepareRecords(prepareRecords);
        if(checkIsChildFolder(prepareRecords, targetParentId, context.getUserId())){
            throw new RPanBusinessException("目标文件夹ID不能是选中文件列表的文件夹ID或者其子文件夹ID");
        }

    }

    /**
     * 执行文件转移的动作
     *
     * @param context
     */
    private void doTransfer(TransferFileConext context) {
        List<RPanUserFile> prepareRecords = context.getPrepareRecords();
        prepareRecords.forEach(record ->{
            record.setParentId(context.getTargetParentId());
            record.setUserId(context.getUserId());
            record.setCreateUser(context.getUserId());
            record.setCreateTime(new Date());
            record.setUpdateUser(context.getUserId());
            record.setUpdateTime(new Date());
            handleDuplicateFilename(record);
        });
        if(!updateBatchById(prepareRecords)){
            throw new RPanBusinessException("文件转移失败");
        }
    }

    /**
     * 文件转移的条件校验
     * 1、目标文件ID必须是一个文件夹
     * 2、选中的文件Id列表中不能含有目标文件夹以及其子文件夹
     *
     * @param context
     */
    private void checkTransferCondition(TransferFileConext context) {
        Long targetParentId = context.getTargetParentId();
        if(!checkIsFolder(getById(targetParentId))){
            throw new RPanBusinessException("目标文件不是一个文件夹");
        }

        List<Long> fileIdList = context.getFileIdList();
        List<RPanUserFile> prepareRecords = listByIds(fileIdList);
        context.setPrepareRecords(prepareRecords);
        if(checkIsChildFolder(prepareRecords, targetParentId, context.getUserId())){
            throw new RPanBusinessException("目标文件夹ID不能是选中文件列表的文件夹ID或者其子文件夹ID");
        }

    }

    /**
     * 校验目标文件夹ID是否是操作的文件夹记录的文件夹ID以及其子文件夹ID
     * 避免循环依赖问题
     * 1、如果操作的列表中没有文件夹，那就直接返回false
     * 2、存在文件夹、拼装文件夹ID以及所有的子文件ID，判断存在即可
     *
     * @param prepareRecords
     * @param targetParentId
     * @param userId
     * @return
     */
    private boolean checkIsChildFolder(List<RPanUserFile> prepareRecords, Long targetParentId, Long userId) {
        prepareRecords = prepareRecords.stream().filter(record -> Objects.equals(FolderFlagEnum.YES.getCode(), record.getFolderFlag())).toList();
        if (CollectionUtils.isEmpty(prepareRecords)) {
            return false;
        }
        List<RPanUserFile> folderRecords = queryFolderRecords(userId);
        Map<Long, List<RPanUserFile>> folderRecordMap = folderRecords.stream().collect(Collectors.groupingBy(RPanUserFile::getParentId));

        List<RPanUserFile> unavailableFolderRecords = Lists.newArrayList();
        unavailableFolderRecords.addAll(prepareRecords);
        prepareRecords.forEach(record -> findAllChildFolderRecords(unavailableFolderRecords, folderRecordMap, record));
        List<Long> unavailableFolderRecordIds = unavailableFolderRecords.stream().map(RPanUserFile::getFileId).toList();
        return unavailableFolderRecordIds.contains(targetParentId);
    }

    /**
     * 查找文件夹的所有子文件夹记录
     * @param unavailableFolderRecords
     * @param folderRecordMap
     * @param record
     */
    private void findAllChildFolderRecords(List<RPanUserFile> unavailableFolderRecords, Map<Long, List<RPanUserFile>> folderRecordMap, RPanUserFile record) {
        if(Objects.isNull(record)){
            return;
        }
        List<RPanUserFile> childFolderRecords = folderRecordMap.get(record.getFileId());
        if(CollectionUtils.isEmpty(childFolderRecords)){
            return;
        }
        unavailableFolderRecords.addAll(childFolderRecords);
        childFolderRecords.forEach(childRecord -> findAllChildFolderRecords(unavailableFolderRecords, folderRecordMap, childRecord));
    }

    /**
     * 拼装文件夹树列表
     *
     * @param folderRecords
     * @return
     */
    private List<FolderTreeNodeVO> assembleFolderTreeNodeVOList(List<RPanUserFile> folderRecords) {
        if (CollectionUtils.isEmpty(folderRecords)) {
            return Collections.emptyList();
        }
        List<FolderTreeNodeVO> nodes = folderRecords.stream()
                .map(fileConverter::rPanUserFile2FolderTreeNodeVO)
                .collect(Collectors.toCollection(ArrayList::new));

        Map<Long, FolderTreeNodeVO> nodeMap = new HashMap<>(nodes.size());
        Map<Long, List<FolderTreeNodeVO>> childrenMap = new HashMap<>();
        for (FolderTreeNodeVO node : nodes) {
            nodeMap.put(node.getId(), node);
            childrenMap.computeIfAbsent(node.getParentId(), k -> new ArrayList<>())
                    .add(node);
        }
        childrenMap.forEach((parentId, children) -> {
            FolderTreeNodeVO parent = nodeMap.get(parentId);
            if (parent != null) {
                parent.getChildren().addAll(children);
            }
        });
        return nodes.stream()
                .filter(node -> Objects.equals(node.getParentId(), FileConstants.TOP_PARENT_ID))
                .collect(Collectors.toList());
    }

    /**
     * 查询用户所有有效的文件夹信息
     *
     * @param userId
     * @return
     */
    private List<RPanUserFile> queryFolderRecords(Long userId) {
        LambdaQueryWrapper<RPanUserFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RPanUserFile::getUserId, userId);
        wrapper.eq(RPanUserFile::getFolderFlag, FolderFlagEnum.YES.getCode());
        wrapper.eq(RPanUserFile::getDelFlag, DelFlagEnum.NO.getCode());
        return list(wrapper);
    }

    /**
     * 执行文件预览的动作
     * 1、获取文件信息 并检查是否存在，防止NPE
     * 2、添加跨域的公共响应头
     * 3、委托文件存储引擎去读取文件内容到相应的输出流中
     * @param record
     * @param response
     */
    private void doPreview(RPanUserFile record, HttpServletResponse response) {
        RPanFile realFileRecord = iFileService.getById(record.getFileId());
        if(Objects.isNull(realFileRecord)){
            throw new RPanBusinessException("当前的文件记录不存在");
        }
        addCommonResponseHeader(response, realFileRecord.getFilePreviewContentType());
        realFile2OutputStream(realFileRecord.getRealPath(), response);
    }
    /**
     * 执行文件下载的动作
     * 1、查询文件的真实存储路径
     * 2、添加跨域的公共响应头
     * 3、拼装下载文件的名称、长度等等响应信息
     * 4、委托文件存储引擎去读取文件内容到相应的输出流中
     *
     * @param record
     * @param response
     */
    private void doDownload(RPanUserFile record, HttpServletResponse response) {
        RPanFile realFileRecord = iFileService.getById(record.getFileId());
        if(Objects.isNull(realFileRecord)){
            throw new RPanBusinessException("当前的文件记录不存在");
        }
        addCommonResponseHeader(response, MediaType.APPLICATION_OCTET_STREAM_VALUE);
        addDownloadAttribute(response, record, realFileRecord);
        realFile2OutputStream(realFileRecord.getRealPath(), response);
    }

    /**
     * 委托文件存储引擎读取文件内容并写入到输出流
     *
     * @param realPath
     * @param response
     */
    @Autowired
    private StorageEngine storageEngine;
    private void realFile2OutputStream(String realPath, HttpServletResponse response) {
        try {
            ReadFileContext context = new ReadFileContext();
            context.setRealPath(realPath);
            context.setOutputStream(response.getOutputStream());
            storageEngine.readFile(context);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RPanBusinessException("文件下载失败");
        }
    }

    /**
     * 添加文件下载的属性信息
     * @param response
     * @param record
     * @param realFileRecord
     */
    private void addDownloadAttribute(HttpServletResponse response, RPanUserFile record, RPanFile realFileRecord) {
        try {
            response.addHeader(FileConstants.CONTENT_DISPOSITION_STR,
                    FileConstants.CONTENT_DISPOSITION_VALUE_PREFIX_STR
                            + new String(record.getFilename().getBytes(FileConstants.GB2312_STR), FileConstants.IOS_8859_1_STR));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RPanBusinessException("文件下载失败");
        }
        response.setContentLengthLong(Long.parseLong(realFileRecord.getFileSize()));
    }

    /**
     * 添加公共的文件读取响应头
     *
     * @param response
     * @param contentTypeValue
     */
    private void addCommonResponseHeader(HttpServletResponse response, String contentTypeValue) {
        response.reset();
        HttpUtil.addCorsResponseHeaders(response);
        response.addHeader("Content-Type",contentTypeValue);
        response.setContentType(contentTypeValue)
        ;
    }

    /**
     * 检查当前文件记录是不是一个文件夹
     *
     * @param record
     * @return
     */
    private boolean checkIsFolder(RPanUserFile record) {
        if(Objects.isNull(record)){
            throw new RPanBusinessException("当前文件记录不存在");
        }
        return FolderFlagEnum.YES.getCode().equals(record.getFolderFlag());
    }

    /**
     * 校验用户的操作权限
     * 1、文件记录必须存在
     * 2、文件的创建者必须是该用户
     *
     * @param record 文件记录信息
     * @param UserId 用户Id
     */
    private void checkOperatePermission(RPanUserFile record, Long UserId) {
        if(Objects.isNull(record)){
            throw new RPanBusinessException("当前文件记录不存在");
        }
        if(!record.getUserId().equals(UserId)){
            throw new RPanBusinessException("你没有该文件的操作权限");
        }
    }

    /**
     * 合并文件分片并保存物理文件记录
     *
     * @param context
     */
    private void mergeFileChunkAndSaveFile(FileChunkMergeContext context) {
        FileChunkMergeAndSaveContext fileChunkMergeAndSaveContext = fileConverter.fileChunkMergeContext2FileChunkMergeAndSaveContext(context);
        iFileService.mergeFileChunkAndSaveFile(fileChunkMergeAndSaveContext);
        context.setRecord(fileChunkMergeAndSaveContext.getRecord());
    }


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




