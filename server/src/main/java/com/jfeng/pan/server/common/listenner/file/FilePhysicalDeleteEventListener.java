//package com.jfeng.pan.server.common.listenner.file;
//
//import cn.hutool.core.collection.CollectionUtil;
//import com.alibaba.fastjson.JSON;
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.jfeng.pan.core.constants.RPanConstants;
//import com.jfeng.pan.server.common.event.file.FilePhysicalDeleteEvent;
//import com.jfeng.pan.server.common.event.log.ErrorLogEvent;
//import com.jfeng.pan.server.modules.file.entity.RPanFile;
//import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
//import com.jfeng.pan.server.modules.file.enums.FolderFlagEnum;
//import com.jfeng.pan.server.modules.file.service.IFileService;
//import com.jfeng.pan.server.modules.file.service.IUserFileService;
//import com.jfeng.pan.storage.engine.core.StorageEngine;
//import com.jfeng.pan.storage.engine.core.context.DeleteFileContext;
//import io.swagger.v3.core.util.Json;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.ApplicationContextAware;
//import org.springframework.context.event.EventListener;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Component;
//
//import java.io.IOException;
//import java.util.List;
//import java.util.Objects;
//
///**
// * 文件物理删除监听器
// */
//@Component
//public class FilePhysicalDeleteEventListener implements ApplicationContextAware {
//
//    @Autowired
//    private IFileService iFileService;
//
//    @Autowired
//    private IUserFileService iUserFileService;
//
//    @Autowired
//    private StorageEngine storageEngine;
//
//    public ApplicationContext applicationContext;
//
//    @Override
//    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
//        this.applicationContext = applicationContext;
//    }
//
//
//    /**
//     * 监听文件物理删除事件执行器
//     * 该执行器时一个资源释放器，释放被物理删除的文件列表中关联记录
//     * 1、查询所有无引用的实体文件记录
//     * 2、删除记录
//     * 3、物理清理文件(委托物理引擎)
//     * @param event
//     */
//    @EventListener(classes = FilePhysicalDeleteEvent.class)
//    @Async(value = "eventListenerTaskExecutor")
//    public void physicalDeleteFile(FilePhysicalDeleteEvent event){
//        List<RPanUserFile> allRecords = event.getAllRecords();
//        if(CollectionUtil.isEmpty(allRecords)){
//            return;
//        }
//        List<Long> realFileIdList = findAllUnusedRealFileIdList(allRecords);
////        if(CollectionUtil.isEmpty(realFileIdList)){
////            return;
////        }
//        List<RPanFile> realFileRecords = iFileService.listByIds(realFileIdList);
//        if (CollectionUtil.isEmpty(realFileRecords)){
//            return;
//        }
//        if(!iFileService.removeByIds(realFileIdList)){
//            applicationContext.publishEvent(
//                    new ErrorLogEvent(this, "实体文件记录："+ JSON.toJSONString(realFileIdList) + ". 物理删除失败，请执行手动删除", RPanConstants.ZERO_LONG)
//            );
//            return;
//        }
//        physicalDeleteByStoreEngine(realFileRecords);
//    }
//
//    /******************************************* private **********************************************************/
//    /**
//     * 委托文件存储引擎执行物理文件的删除
//     * @param realFileRecords
//     */
//    private void physicalDeleteByStoreEngine(List<RPanFile> realFileRecords) {
//        List<String> realPathList = realFileRecords.stream().map(RPanFile::getRealPath).toList();
//        DeleteFileContext deleteFileContext = new DeleteFileContext();
//        deleteFileContext.setRealPathList(realPathList);
//
//        try {
//            storageEngine.delete(deleteFileContext);
//        } catch (IOException e) {
//            applicationContext.publishEvent(
//                    new ErrorLogEvent(this, "实体文件："+ JSON.toJSONString(realPathList) + ". 物理删除失败，请执行手动删除", RPanConstants.ZERO_LONG)
//            );
//        }
//    }
//
//    /**
//     * 查找所有没有被引用的文件集合
//     * @param allRecords
//     * @return
//     */
//    private List<Long> findAllUnusedRealFileIdList(List<RPanUserFile> allRecords) {
//        return allRecords.stream()
//                .filter(record -> Objects.equals(record.getFolderFlag(), FolderFlagEnum.NO.getCode()))
//                .filter(this::isUnused)
//                .map(RPanUserFile::getRealFileId)
//                .toList();
//    }
//
//    /**
//     * 校验文件的真实文件ID是不是没有被引用
//     *
//     * @param record
//     * @return
//     */
//    private boolean isUnused(RPanUserFile record) {
//        // TODO 这里是否存在N+1的问题？
//        LambdaQueryWrapper<RPanUserFile> queryWrapper = new LambdaQueryWrapper<>();
//        queryWrapper.eq(RPanUserFile::getRealFileId, record.getRealFileId());
//        return iUserFileService.count(queryWrapper) == RPanConstants.ZERO_LONG;
//    }
//
//
//}
