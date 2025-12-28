package com.jfeng.pan.server.modules.recycle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.server.common.event.file.FileRestoreEvent;
import com.jfeng.pan.server.modules.file.context.QueryFileListContext;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.file.enums.DelFlagEnum;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import com.jfeng.pan.server.modules.recycle.context.QueryRecycleFileListContext;
import com.jfeng.pan.server.modules.recycle.context.RestoreContext;
import com.jfeng.pan.server.modules.recycle.service.IRecycleService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 回收站模块业务处理类
 */
@Service
public class RecycleServiceImpl implements IRecycleService, ApplicationContextAware {

    @Autowired
    private IUserFileService iUserFileService;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    /**
     * 查询用户的回收站文件列表
     *
     * @param context
     * @return
     */
    @Override
    public List<RPanUserFileVO> recycles(QueryRecycleFileListContext context) {
        QueryFileListContext queryFileListContext = new QueryFileListContext();
        queryFileListContext.setUserId(context.getUserId());
        queryFileListContext.setDelFlag(DelFlagEnum.YES.getCode());
        return iUserFileService.getFileList(queryFileListContext);
    }

    /**
     * 文件还原
     * 1、检查操作权限
     * 2、是不是可以还原 (文件重命名、)
     * 3、执行文件还原的操作
     * 4、执行文件还原的后置操作（钩子函数）
     *
     * @param context
     */
    @Override
    public void restore(RestoreContext context) {
        checkRestorePermission(context);
        checkRestoreFilename(context);
        doRestore(context);
        afterRestore(context);
    }

    /*************************************************** private ******************************************************/
    /**
     * 文件还原的后置操作
     * 1、发布文件还原事件
     * 2、
     * @param context
     */
    private void afterRestore(RestoreContext context) {
        FileRestoreEvent event = new FileRestoreEvent(this, context.getFileIdList());
        applicationContext.publishEvent(event);
    }

    /**
     *  执行文件还原的动作
     *
     * @param context
     */
    private void doRestore(RestoreContext context) {
        List<RPanUserFile> records = context.getRecords();
        records.forEach(record ->{
            record.setDelFlag(DelFlagEnum.NO.getCode());
            record.setCreateUser(context.getUserId());
            record.setUpdateTime(new Date());
                });
            boolean updateFlag = iUserFileService.updateBatchById(records);
            if(!updateFlag){
                throw new RPanBusinessException("文件还原失败");
            }
    }

    /**
     * 检查要还原的名称是不是被占用
     * 1、要还原的文件列表中有同一个文件夹下面相同名称的文件 不允许还原
     * 2、要还原的文件当前的父文件夹下面存在同名文件 我们不允许还原
     *
     * @param context
     */
    private void checkRestoreFilename(RestoreContext context) {
        List<RPanUserFile> records = context.getRecords();

        Set<String> filenameSet = records.stream().map(record -> record.getFilename() + RPanConstants.COMMON_SEPARATOR + record.getParentId()).collect(Collectors.toSet());
        if (filenameSet.size() != records.size()){
            throw new RPanBusinessException("文件还原失败，该还原文件中存在同名文件，请逐个还原并重命名");
        }
        // TODO 这里存在N+1问题
        for(RPanUserFile record : records){
            LambdaQueryWrapper<RPanUserFile> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(RPanUserFile::getUserId, context.getUserId());
            queryWrapper.eq(RPanUserFile::getParentId, record.getParentId());
            queryWrapper.eq(RPanUserFile::getFilename, record.getFilename());
            queryWrapper.eq(RPanUserFile::getDelFlag, DelFlagEnum.NO.getCode());
            if(iUserFileService.count(queryWrapper) > 0){
                throw new RPanBusinessException("文件："+ record.getFilename() + "还原失败，该文件夹下面已经存在了相同的文件或文件夹，请重命名之后再执行文件还原");
            }
        }
    }

    /**
     * 检查文件操作权限
     * @param context
     */
    private void checkRestorePermission(RestoreContext context) {
        List<Long> fileIdList = context.getFileIdList();

        List<RPanUserFile> records = iUserFileService.listByIds(fileIdList);
        if(CollectionUtils.isEmpty(records)){
            throw new RPanBusinessException("文件还原失败");
        }
        Set<Long> userIdSet = records.stream().map(RPanUserFile::getUserId).collect(Collectors.toSet());
        if (userIdSet.size() > 1){
            throw new RPanBusinessException("您无权执行文件还原");
        }
        if (!userIdSet.contains(context.getUserId())){
            throw new RPanBusinessException("您无权执行文件还原");
        }

        context.setRecords(records);
    }


}
