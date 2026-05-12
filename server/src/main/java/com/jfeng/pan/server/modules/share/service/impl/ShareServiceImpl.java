package com.jfeng.pan.server.modules.share.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.bloom.filter.core.BloomFilter;
import com.jfeng.pan.bloom.filter.core.BloomFilterManager;
import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.response.ResponseCode;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.core.utils.JwtUtil;
import com.jfeng.pan.core.utils.UUIDUtil;
import com.jfeng.pan.server.common.cache.ManualCacheService;
import com.jfeng.pan.server.common.config.RPanServerConfig;
import com.jfeng.pan.server.common.stream.channel.PanChannel;
import com.jfeng.pan.server.common.stream.event.log.ErrorLogEvent;
import com.jfeng.pan.server.modules.file.constants.FileConstants;
import com.jfeng.pan.server.modules.file.context.CopyFileContext;
import com.jfeng.pan.server.modules.file.context.FileDownloadContext;
import com.jfeng.pan.server.modules.file.context.QueryFileListContext;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.file.enums.DelFlagEnum;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import com.jfeng.pan.server.modules.share.constants.ShareConstants;
import com.jfeng.pan.server.modules.share.context.*;
import com.jfeng.pan.server.modules.share.entity.RPanShare;
import com.jfeng.pan.server.modules.share.entity.RPanShareFile;
import com.jfeng.pan.server.modules.share.enums.ShareDayTypeEnum;
import com.jfeng.pan.server.modules.share.enums.ShareStatusEnum;
import com.jfeng.pan.server.modules.share.service.IShareFileService;
import com.jfeng.pan.server.modules.share.service.IShareService;
import com.jfeng.pan.server.modules.share.mapper.RPanShareMapper;
import com.jfeng.pan.server.modules.share.vo.*;
import com.jfeng.pan.server.modules.user.entity.RPanUser;
import com.jfeng.pan.server.modules.user.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Sets;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 16837
 * @description 针对表【r_pan_share(用户分享表)】的数据库操作Service实现
 * @createDate 2025-11-06 19:24:38
 */
@Service
@Slf4j
public class ShareServiceImpl extends ServiceImpl<RPanShareMapper, RPanShare>
        implements IShareService {

    private static final String BLOOM_FILTER_NAME = "SHARE_SIMPLE_DETAIL";

    @Autowired
    private RPanServerConfig config;

    @Autowired
    private IShareFileService iShareFileService;

    @Autowired
    private IUserFileService iUserFileService;

    @Autowired
    private IUserService iUserService;

    @Autowired
    private BloomFilterManager bloomFilterManager;

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    @Qualifier(value = "shareManualCacheService")
    private ManualCacheService<RPanShare> cacheService;

    /**
     * 创建文件分享链接
     * 1、拼装分享实体，保存到数据库
     * 2、保存分享和对应文件的关联关系
     * 3、拼装返回实体并返回
     *
     * @param context
     * @return
     */
    @Transactional(rollbackFor = RPanBusinessException.class)
    @Override
    public ShareUrlVO create(CreateShareUrlContext context) {
        saveShare(context);
        saveShareFiles(context);
        ShareUrlVO vo = assembleShareVO(context);
        afterCreate(context, vo);
        return vo;
    }

    /**
     * 查询用户的分享列表
     *
     * @param context
     * @return
     */
    @Override
    public List<ShareUrlListVO> getShares(QueryShareListContext context) {

        return baseMapper.selectShareVOListByUserId(context.getUserId());
    }

    /**
     * 取消分享链接
     * 1、校验用户操作权限
     * 2、删除对应的分享记录
     * 3、删除对应的分享文件关联关系记录
     *
     * @param context
     */
    @Transactional(rollbackFor = RPanBusinessException.class)
    @Override
    public void cancelShare(CancelShareContext context) {
        checkUserCancelSharePermission(context);
        doCancelShare(context);
        doCancelShareFiles(context);

    }

    /**
     * 校验分享码
     * 1、检查分享的状态是不是正常
     * 2、校验分享的分享码是不是正确
     * 3、生成一个短时间的分享token，返回给上游
     *
     * @param context
     * @return
     */
    @Override
    public String checkShareCode(CheckShareCodeContext context) {
        RPanShare record = checkShareStatus(context.getShareId());
        context.setRecord(record);
        doCheckShareCode(context);
        return generateShareToken(context);
    }

    /**
     * 查询分享的详情
     * 1、校验分享的状态
     * 2、初始化分享实体
     * 3、查询分享的主体信息
     * 4、查询分享的文件列表
     * 5、查询分享者的信息
     *
     * @param context
     * @return
     */
    @Override
    public ShareDetailVO detail(QueryShareDetailContext context) {
        RPanShare record = checkShareStatus(context.getShareId());
        context.setRecord(record);
        initShareVO(context);
        assembleMainShareInfo(context);
        assembleShareFilesInfo(context);
        assembleShareUserInfo(context);
        return context.getVo();
    }

    /**
     * 查询分享的简单详情
     * 1、查询分享的状态
     * 2、初始化分享实体
     * 3、查询分享的主题信息
     * 4、查询分享者的信息
     * 
     * @param context
     * @return
     */
    @Override
    public ShareSimpleDetailVO simpleDetail(QueryShareSimpleDetailContext context) {
        RPanShare record = checkShareStatus(context.getShareId());
        context.setRecord(record);
        initShareSimpleVO(context);
        assembleMainShareSimpleInfo(context);
        assembleShareSimpleUserInfo(context);
        return context.getVo();
    }

    /**
     * 获取下一级的文件列表
     * 1、校验分享的状态
     * 2、校验文件的ID是在分享的文件列表
     * 3、查询对应的文件子文件列表
     *
     * @param context
     * @return
     */
    @Override
    public List<RPanUserFileVO> fileList(QueryChildFileListContext context) {
        RPanShare record = checkShareStatus(context.getShareId());
        context.setRecord(record);
        List<RPanUserFileVO> allUserFileRecord = checkFileIdIsOnShareStatusAndGetAllShareUserFiles(context.getShareId(),
                Lists.newArrayList(context.getParentId()));
        Map<Long, List<RPanUserFileVO>> parentIdFileListMap = allUserFileRecord.stream()
                .collect(Collectors.groupingBy(RPanUserFileVO::getParentId));
        List<RPanUserFileVO> rPanUserFileVOList = parentIdFileListMap.get(context.getParentId());
        if (CollectionUtil.isEmpty(rPanUserFileVOList)) {
            return Lists.newArrayList();
        }
        return rPanUserFileVOList;
    }

    /**
     * 转存至我的网盘
     * 1、校验分享状态
     * 2、校验文件传过来的文件ID是否合法
     * 3、委托文件模块对文件拷贝操作
     * 
     * @param context
     */
    @Override
    public void saveFiles(ShareSaveContext context) {
        checkShareStatus(context.getShareId());
        checkFileIdIsOnShareStatus(context.getShareId(), context.getFileIdList());
        doSaveFiles(context);

    }

    /**
     * 分享文件下载
     * 1、校验分享的状态
     * 2、校验文件ID的合法性
     * 3、执行文件下载的动作
     *
     * @param context
     */
    @Override
    public void download(ShareFileDownloadContext context) {
        checkShareStatus(context.getShareId());
        checkFileIdIsOnShareStatus(context.getShareId(), Lists.newArrayList(context.getFileId()));
        doDownload(context);
    }

    /**
     * <p>
     * <h3>刷新受影响的对应的分享的状态</h3>
     * 通过文件ID将分享链接进行状态修改
     * 当发生文件删除或还原时，执行刷新分享列表的操作
     *
     * <li>1、查询所有受影响的分享的ID集合</li>
     * <li>2、去判断每一个分享对应的文件以及所有的父文件信息均为正常，该种情况，把分享的状态变为正常</li>
     * <li>3、如果有分享的文件或者是父文件信息被删除，变更该分享的状态为有文件被删除</li>
     * </p>
     *
     * @param allAvailableFileIdList
     */
    @Override
    public void refreshShareStatus(List<Long> allAvailableFileIdList) {
        List<Long> shareIdList = getShareIdListByFileIdList(allAvailableFileIdList);
        if (CollectionUtils.isEmpty(shareIdList)) {
            return;
        }
        Set<Long> shareIdSet = Sets.newHashSet(shareIdList);
        shareIdSet.forEach(this::refreshOneShareStatus);
    }

    /**
     * 滚动查询已存在的分享ID
     *
     * @param startId
     * @param limit
     * @return
     */
    @Override
    public List<Long> rollingQueryShareId(long startId, long limit) {
        return baseMapper.rollingQueryShareId(startId, limit);
    }

    /**
     * 根据ID查询数据库和缓存
     * 
     * @param id 序列化ID
     * @return
     */
    @Override
    public RPanShare getById(Serializable id) {
        return cacheService.getById(id);
        // return super.getById(id);
    }

    /**
     * 根据ID更新数据库和缓存
     * 
     * @param entity 实体信息
     * @return
     */
    @Override
    public boolean updateById(RPanShare entity) {
        return cacheService.updateById(entity.getShareId(), entity);
        // return super.updateById(entity);
    }

    /**
     * 根据ID删除实体和缓存
     * 
     * @param entity 实体信息
     * @return
     */
    @Override
    public boolean removeById(RPanShare entity) {
        return cacheService.removeById(entity.getShareId());
        // return super.removeById(entity);
    }

    /**
     * 根据ID列表 批量查询
     * 
     * @param idList ID列表
     * @return
     */
    @Override
    public List<RPanShare> listByIds(Collection<? extends Serializable> idList) {
        return cacheService.getByIds(idList);
        // return super.listByIds(idList);
    }

    /**
     * 根据enetiy列表 批量更新数据库和缓存
     * 
     * @param entityList entity列表
     * @return
     */
    @Override
    public boolean updateBatchById(Collection<RPanShare> entityList) {
        if (CollectionUtils.isEmpty(entityList)) {
            return true;
        }
        Map<Long, RPanShare> entityMap = entityList.stream()
                .collect(Collectors.toMap(RPanShare::getShareId, entity -> entity));
        return cacheService.updateByIds(entityMap);
        // return super.updateBatchById(entityList);
    }

    @Override
    public boolean removeBatchByIds(Collection<?> list) {
        return cacheService.removeByIds((Collection<? extends Serializable>) list);
        // return super.removeBatchByIds(list);
    }

    /*******************************************************************
     * private
     *****************************************************************************/

    /**
     * 创建分享链接后置处理
     *
     * @param context
     * @param vo
     */
    private void afterCreate(CreateShareUrlContext context, ShareUrlVO vo) {
        BloomFilter<Long> bloomFilter = bloomFilterManager.getFilter(BLOOM_FILTER_NAME);
        if (Objects.nonNull(bloomFilter)) {
            bloomFilter.put(context.getRecord().getShareId());
            log.info("create share, add share id to bloom filter, share id is {}", context.getRecord().getShareId());
        }
    }

    /**
     * 刷新一个分享ID的分享状态
     * 1、查询对应的分享信息是否有效
     * 2、判断分享对应的文件以及所有的父文件信息是否正常，这种情况把分享状态变成正常
     * 3、如果分享文件
     * 
     * @param shareId 分享ID
     */
    private void refreshOneShareStatus(Long shareId) {
        RPanShare record = getById(shareId);
        if (Objects.isNull(record)) {
            return;
        }
        ShareStatusEnum shareStatus = ShareStatusEnum.NORMAL;
        if (!checkShareFileAvailable(shareId)) {
            shareStatus = ShareStatusEnum.FILE_DELETE;
        }

        if (Objects.equals(record.getShareStatus(), shareStatus.getCode())) {
            return;
        }
        doChangeShareStatus(record, shareStatus);
    }

    /**
     * 执行修改分享ID的状态信息
     *
     * @param record
     * @param shareStatus
     */
    private void doChangeShareStatus(RPanShare record, ShareStatusEnum shareStatus) {
        record.setShareStatus(shareStatus.getCode());
        if (!updateById(record)) {
            ErrorLogEvent event = new ErrorLogEvent("更新分享状态失败，请手动更改状态，分享ID为：" +
                    record.getShareId() + ", 分享" +
                    "状态改为：" + shareStatus.getCode(),
                    RPanConstants.ZERO_LONG);
            streamBridge.send(PanChannel.ERROR_LOG_OUT, event);
        }
    }

    /**
     * 检查分享ID内所有文件及其父文件是否是可用的
     *
     * @param shareId
     * @return
     */
    private boolean checkShareFileAvailable(Long shareId) {
        List<Long> shareFileIdList = getShareFileIdList(shareId);
        for (Long fileId : shareFileIdList) {
            if (!checkUpFileAvailable(fileId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查文件及其所有父文件信息均为正常
     *
     * @param fileId
     * @return
     */
    private boolean checkUpFileAvailable(Long fileId) {
        RPanUserFile record = iUserFileService.getById(fileId);
        if (Objects.isNull(record)) {
            return false;
        }
        if (Objects.equals(DelFlagEnum.YES.getCode(), record.getDelFlag())) {
            return false;
        }
        if (Objects.equals(record.getParentId(), FileConstants.TOP_PARENT_ID)) {
            return true;
        }
        return checkUpFileAvailable(record.getParentId());
    }

    /**
     * 通过文件ID获取分享ID集合
     * 
     * @param allAvailableFileIdList
     * @return
     */
    private List<Long> getShareIdListByFileIdList(List<Long> allAvailableFileIdList) {
        LambdaQueryWrapper<RPanShareFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(RPanShareFile::getShareId);
        wrapper.in(RPanShareFile::getFileId, allAvailableFileIdList);
        return iShareFileService.listObjs(wrapper, value -> (Long) value);
    }

    /**
     * 执行分享文件下载的动作
     * 
     * @param context
     */
    private void doDownload(ShareFileDownloadContext context) {
        FileDownloadContext fileDownloadContext = new FileDownloadContext();
        fileDownloadContext.setFileId(context.getFileId());
        fileDownloadContext.setUserId(context.getUserId());
        fileDownloadContext.setResponse(context.getResponse());
        iUserFileService.downloadWithoutCheckUser(fileDownloadContext);
    }

    /**
     * 执行将分享文件集合保存到父文件夹下
     * 委托文件模块进行转存
     * 
     * @param context
     */
    private void doSaveFiles(ShareSaveContext context) {
        CopyFileContext copyFileContext = new CopyFileContext();
        copyFileContext.setFileIdList(context.getFileIdList());
        copyFileContext.setTargetParentId(context.getParentId());
        copyFileContext.setUserId(context.getUserId());
        iUserFileService.copy(copyFileContext);
    }

    /**
     * 校验分享的文件ID是否属于某一分享
     *
     * @param shareId
     * @param fileIdList
     */
    private void checkFileIdIsOnShareStatus(Long shareId, List<Long> fileIdList) {
        checkFileIdIsOnShareStatusAndGetAllShareUserFiles(shareId, fileIdList);
    }

    /**
     * 校验文件是否处于分享状态，返回该分享的所有文件列表
     *
     * @param shareId
     * @param fileIdList
     * @return
     */
    private List<RPanUserFileVO> checkFileIdIsOnShareStatusAndGetAllShareUserFiles(Long shareId,
            List<Long> fileIdList) {
        List<Long> shareFileIdList = getShareFileIdList(shareId);
        if (CollectionUtil.isEmpty(shareFileIdList)) {
            return Lists.newArrayList();
        }
        List<RPanUserFile> allFileRecordsByFileIdList = iUserFileService
                .findAllFileRecordsByFileIdList(shareFileIdList);
        if (CollectionUtils.isEmpty(allFileRecordsByFileIdList)) {
            return Lists.newArrayList();
        }
        List<RPanUserFile> allFileRecords = allFileRecordsByFileIdList.stream()
                .filter(Objects::nonNull)
                .filter(record -> Objects.equals(DelFlagEnum.NO.getCode(), record.getDelFlag()))
                .toList();
        Set<Long> allFileRecordsIdSet = allFileRecords.stream().map(RPanUserFile::getFileId)
                .collect(Collectors.toSet());
        if (allFileRecordsIdSet.containsAll(fileIdList)) {
            return iUserFileService.transferVOList(allFileRecords);
        }
        throw new RPanBusinessException(ResponseCode.SHARE_FILE_MISS);
    }

    /**
     * 拼装简单分享详情实体信息
     * 
     * @param context
     */
    private void assembleShareSimpleUserInfo(QueryShareSimpleDetailContext context) {
        RPanUser record = iUserService.getById(context.getRecord().getCreateUser());
        if (Objects.isNull(record)) {
            throw new RPanBusinessException("用户信息查询失败");
        }
        ShareUserInfoVO shareUserInfoVO = new ShareUserInfoVO();
        shareUserInfoVO.setUserId(record.getUserId());
        shareUserInfoVO.setUsername(encryptUsername(record.getUsername()));
        context.getVo().setShareUserInfoVO(shareUserInfoVO);
    }

    /**
     * 填充简单分享详情的实体信息
     * 
     * @param context
     */
    private void assembleMainShareSimpleInfo(QueryShareSimpleDetailContext context) {
        RPanShare record = context.getRecord();
        context.getVo().setShareId(record.getShareId());
        context.getVo().setShareName(record.getShareName());
    }

    /**
     * 初始化简单分享详情的VO对象
     * 
     * @param context
     */
    private void initShareSimpleVO(QueryShareSimpleDetailContext context) {
        ShareSimpleDetailVO vo = new ShareSimpleDetailVO();
        context.setVo(vo);
    }

    /**
     * 查询分享者的信息
     *
     * @param context
     */
    private void assembleShareUserInfo(QueryShareDetailContext context) {
        RPanUser record = iUserService.getById(context.getRecord().getCreateUser());
        if (Objects.isNull(record)) {
            throw new RPanBusinessException("用户信息查询失败");
        }
        ShareUserInfoVO shareUserInfoVO = new ShareUserInfoVO();
        shareUserInfoVO.setUserId(record.getUserId());
        shareUserInfoVO.setUsername(encryptUsername(record.getUsername()));
        context.getVo().setShareUserInfoVO(shareUserInfoVO);
    }

    /**
     * 加密用户名称
     *
     * @param username
     * @return
     */
    private String encryptUsername(String username) {
        StringBuilder stringBuffer = new StringBuilder(username);
        stringBuffer.replace(RPanConstants.TWO_INT, username.length() - RPanConstants.TWO_INT,
                RPanConstants.COMMON_ENCRYPT_STR);
        return stringBuffer.toString();
    }

    /**
     * 查询分享对应的文件列表
     * 1、查询对应的文件ID集合
     * 2、根据分分享ID查询文件列表信息
     *
     * @param context
     */
    private void assembleShareFilesInfo(QueryShareDetailContext context) {
        List<Long> fileIdList = getShareFileIdList(context.getShareId());

        QueryFileListContext queryFileListContext = new QueryFileListContext();
        queryFileListContext.setUserId(context.getRecord().getCreateUser());
        queryFileListContext.setDelFlag(DelFlagEnum.NO.getCode());
        queryFileListContext.setFileIdList(fileIdList);
        List<RPanUserFileVO> rPanUserFileVOList = iUserFileService.getFileList(queryFileListContext);
        context.getVo().setRPanUserFileVOList(rPanUserFileVOList);
    }

    /**
     * 查询分享对应的文件Id集合
     * 
     * @param shareId
     * @return
     */
    private List<Long> getShareFileIdList(Long shareId) {
        if (Objects.isNull(shareId)) {
            return Lists.newArrayList();
        }
        LambdaQueryWrapper<RPanShareFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(RPanShareFile::getFileId);
        wrapper.eq(RPanShareFile::getShareId, shareId);
        return iShareFileService.listObjs(wrapper, value -> (Long) value);
    }

    /**
     * 查询分享的主体信息
     *
     * @param context
     */
    private void assembleMainShareInfo(QueryShareDetailContext context) {
        RPanShare record = context.getRecord();
        ShareDetailVO vo = context.getVo();
        vo.setShareId(record.getShareId());
        vo.setShareName(record.getShareName());
        vo.setShareDay(record.getShareDay());
        vo.setShareEndTime(record.getShareEndTime());
        vo.setCreateTime(record.getCreateTime());
    }

    /**
     * 初始化文件详情的VO实体
     *
     * @param context
     */
    private void initShareVO(QueryShareDetailContext context) {
        ShareDetailVO vo = new ShareDetailVO();
        context.setVo(vo);
    }

    /**
     * 生成一个短期的分享token
     *
     * @param context
     * @return
     */
    private String generateShareToken(CheckShareCodeContext context) {
        RPanShare record = context.getRecord();
        return JwtUtil.generateToken(UUIDUtil.getUUID(), ShareConstants.SHARE_ID, record.getShareId(),
                ShareConstants.ONE_HOUR_LONG);
    }

    /**
     * 校验文件的分享码是否正确
     * 
     * @param context
     */
    private void doCheckShareCode(CheckShareCodeContext context) {
        RPanShare record = context.getRecord();
        if (!Objects.equals(context.getShareCode(), record.getShareCode())) {
            throw new RPanBusinessException("分享码错误");
        }
    }

    /**
     * 校验分享的状态是不是正常
     * 
     * @param shareId
     * @return
     */
    private RPanShare checkShareStatus(Long shareId) {
        RPanShare record = getById(shareId);

        if (Objects.isNull(record)) {
            throw new RPanBusinessException(ResponseCode.SHARE_CANCELLED);
        }

        if (Objects.equals(ShareStatusEnum.FILE_DELETE.getCode(), record.getShareStatus())) {
            throw new RPanBusinessException(ResponseCode.SHARE_FILE_MISS);
        }

        if (Objects.equals(ShareDayTypeEnum.PERMANENT_VALIDITY.getCode(), record.getShareDayType())) {
            return record;
        }

        if (record.getShareEndTime().before(new Date())) {
            throw new RPanBusinessException(ResponseCode.SHARE_EXPIRED);
        }

        return record;
    }

    /**
     * 取消文件和分享的关联关系数据
     * 
     * @param context
     */
    private void doCancelShareFiles(CancelShareContext context) {
        LambdaQueryWrapper<RPanShareFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(RPanShareFile::getShareId, context.getShareIdList());
        queryWrapper.eq(RPanShareFile::getCreateUser, context.getUserId());
        if (!iShareFileService.remove(queryWrapper)) {
            throw new RPanBusinessException("取消分享失败");
        }
    }

    /**
     * 取消文件分享的动作
     *
     * @param context
     */
    private void doCancelShare(CancelShareContext context) {
        List<Long> shareIdList = context.getShareIdList();
        if (!removeBatchByIds(shareIdList)) {
            throw new RPanBusinessException("取消分享失败");
        }
    }

    /**
     * 检查用户是否拥有取消对应分享链接的权限
     *
     * @param context
     */
    private void checkUserCancelSharePermission(CancelShareContext context) {
        List<Long> shareIdList = context.getShareIdList();
        Long userId = context.getUserId();
        List<RPanShare> records = listByIds(shareIdList);
        if (CollectionUtil.isEmpty(records)) {
            throw new RPanBusinessException("您无权限操作取消分享的动作");
        }
        for (RPanShare record : records) {
            if (!Objects.equals(record.getCreateUser(), userId)) {
                throw new RPanBusinessException("您无权限操作取消分享的动作");
            }
        }
    }

    /**
     * 拼装对应的返回VO
     *
     * @param context
     * @return
     */
    private ShareUrlVO assembleShareVO(CreateShareUrlContext context) {
        RPanShare record = context.getRecord();
        ShareUrlVO vo = new ShareUrlVO();
        vo.setShareId(record.getShareId());
        vo.setShareName(record.getShareName());
        vo.setShareUrl(record.getShareUrl());
        vo.setShareCode(record.getShareCode());
        vo.setShareStatus(record.getShareCode());
        return vo;
    }

    /**
     * 保存分享和分享文件的关联关系
     *
     * @param context
     */
    private void saveShareFiles(CreateShareUrlContext context) {
        SaveShareFilesContext saveShareFilesContext = new SaveShareFilesContext();
        saveShareFilesContext.setShareId(context.getRecord().getShareId());
        saveShareFilesContext.setShareFileIdList(context.getShareFileIdList());
        saveShareFilesContext.setUserId(context.getUserId());
        iShareFileService.saveShareFiles(saveShareFilesContext);
    }

    /**
     * 拼装返回的实体，并保存到数据库中
     *
     * @param context
     */
    private void saveShare(CreateShareUrlContext context) {
        RPanShare record = new RPanShare();
        record.setShareId(IdUtil.get());
        record.setShareName(context.getShareName());
        record.setShareType(context.getShareType());
        record.setShareDayType(context.getShareDayType());

        Integer shareDay = ShareDayTypeEnum.getShareDayByCode(context.getShareDayType());
        if (Objects.equals(RPanConstants.MINUS_ONE_INT, shareDay)) {
            throw new RPanBusinessException("非法的分享天数");
        }
        record.setShareDay(shareDay);

        record.setShareEndTime(DateUtil.offsetDay(new Date(), shareDay));
        record.setShareUrl(createShareUlr(record.getShareId()));
        record.setShareCode(createShareCode());
        record.setShareStatus(ShareStatusEnum.NORMAL.getCode());
        record.setCreateUser(context.getUserId());
        record.setCreateTime(new Date());

        if (!save(record)) {
            throw new RPanBusinessException("保存分享信息失败");
        }
        context.setRecord(record);
    }

    /**
     * 创建分享的分享码
     *
     * @return
     */
    private String createShareCode() {
        return RandomStringUtils.random(4, 'a', 'z', false, false);
    }

    /**
     * 创建分享的URL
     * 
     * @param shareId
     * @return
     */
    private String createShareUlr(Long shareId) {
        if (Objects.isNull(shareId)) {
            throw new RPanBusinessException("分享的ID不能为空");
        }
        String sharePrefix = config.getSharePrefix();
        if (RPanConstants.SLASH_STR.equals(sharePrefix.charAt(sharePrefix.length() - 1))) {
            sharePrefix += RPanConstants.SLASH_STR;
        }
        return sharePrefix + URLEncoder.encode(IdUtil.encrypt(shareId));
    }

}
