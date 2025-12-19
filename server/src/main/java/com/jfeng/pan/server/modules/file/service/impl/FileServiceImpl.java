package com.jfeng.pan.server.modules.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.FileUtil;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.event.log.ErrorLogEvent;
import com.jfeng.pan.server.modules.file.context.FileChunkMergeAndSaveContext;
import com.jfeng.pan.server.modules.file.context.FileSaveContext;
import com.jfeng.pan.server.modules.file.entity.RPanFile;
import com.jfeng.pan.server.modules.file.entity.RPanFileChunk;
import com.jfeng.pan.server.modules.file.service.IFileChunkService;
import com.jfeng.pan.server.modules.file.service.IFileService;
import com.jfeng.pan.server.modules.file.mapper.RPanFileMapper;
import com.jfeng.pan.storage.engine.core.StorageEngine;
import com.jfeng.pan.storage.engine.core.context.DeleteFileContext;
import com.jfeng.pan.storage.engine.core.context.MergeFileContext;
import com.jfeng.pan.storage.engine.core.context.StoreFileContext;
import org.assertj.core.util.Lists;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
* @author 16837
* @description 针对表【r_pan_file(物理文件信息表)】的数据库操作Service实现
* @createDate 2025-11-06 19:22:58
*/
@Service(value = "userFileService")
public class FileServiceImpl extends ServiceImpl<RPanFileMapper, RPanFile> implements IFileService, ApplicationContextAware {

    @Autowired
    private StorageEngine storageEngine;

    private ApplicationContext applicationContext;

    @Autowired
    private IFileChunkService iFileChunkService;


    /**
     * 广播机器出错的事件
     * @param applicationContext the ApplicationContext object to be used by this object
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext =  applicationContext;
    }


    /**
     * 上传单文件并保存实体记录
     * 1、上传单文件
     * 2、保存实体记录
     *
     * @param context
     */
    @Override
    public void saveFile(FileSaveContext context) {
        storeMultipartFile(context);
        RPanFile recode = doSaveFile(context.getFilename(),
                context.getRealPath(),
                context.getTotalSize(),
                context.getIdentifier(),
                context.getUserId());
        context.setRecode(recode);
    }

    /**
     * 合并物理文件并保存文件记录
     * 1、委托物理存储引擎合并文件分片
     * 2、保存物理文件记录
     * @param context
     */
    @Override
    public void mergeFileChunkAndSaveFile(FileChunkMergeAndSaveContext context) {
        doMergeFileChunk(context);
        RPanFile record = doSaveFile(context.getFilename(), context.getRealPath(), context.getTotalSize(), context.getIdentifier(), context.getUserId());
        context.setRecord(record);

    }

    /**
     * 委托物理存储引擎合并文件分片
     * 1、查询文件分片的记录
     * 2、根据分片记录去合并物理文件
     * 3、删除文件的分片记录
     * 4、封装合并文件的真实存储路径到文件的上下文信息中
     *
     * @param context
     */
    private void doMergeFileChunk(FileChunkMergeAndSaveContext context) {
        LambdaQueryWrapper<RPanFileChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RPanFileChunk::getIdentifier, context.getIdentifier());
        queryWrapper.eq(RPanFileChunk::getCreateUser, context.getUserId());
        queryWrapper.gt(RPanFileChunk::getExpirationTime, new Date());
        List<RPanFileChunk> chunkRecordList = iFileChunkService.list(queryWrapper);

        if(CollectionUtils.isEmpty(chunkRecordList)){
            throw new RPanBusinessException("该文件未找到分片记录");
        }

        List<String> realPathList = chunkRecordList.stream()
                .sorted(Comparator.comparing(RPanFileChunk::getChunkNumber))
                .map(RPanFileChunk::getRealPath).toList();
        // TODO 委托存储引擎去合并文件分片
        try{

            MergeFileContext mergeFileContext = new MergeFileContext();
            mergeFileContext.setFilename(context.getFilename());
            mergeFileContext.setIdentifier(context.getIdentifier());
            mergeFileContext.setUserId(context.getUserId());
            mergeFileContext.setRealPathList(realPathList);
            storageEngine.mergeFile(mergeFileContext);

            context.setRealPath(mergeFileContext.getRealPath());

        }catch (IOException e){
            e.printStackTrace();
            throw new RPanBusinessException("文件分片合并失败");
        }


        List<Long> fileChunkRecordIdList = chunkRecordList.stream().map(RPanFileChunk::getId).toList();
        iFileChunkService.removeByIds(fileChunkRecordIdList);

        // TODO 封装实体文件的真实存储路径
    }

    /****************************************************** private ***************************************************************/

    /**
     * 保存文件实体记录
     * @param filename
     * @param realPath
     * @param totalSize
     * @param identifier
     * @param userId
     */
    private RPanFile doSaveFile(String filename, String realPath, Long totalSize, String identifier, Long userId) {
        RPanFile recode = asembleRPanFile(filename,realPath,totalSize,identifier,userId);
        if(!save(recode)){
            // TODO 删除已经删除的物理文件
            try {
                DeleteFileContext deleteFileContext = new DeleteFileContext();
                deleteFileContext.setRealPathList(Lists.newArrayList(realPath));
                storageEngine.delete(deleteFileContext);
            } catch (IOException e) {
                e.printStackTrace();
                ErrorLogEvent errorLogEvent = new ErrorLogEvent(this, "文件物理删除失败，请执行手动删除！文件路径："+realPath, userId);
                applicationContext.publishEvent(errorLogEvent);

            }
        }
        return recode;

    }

    /**
     * 上传单文件
     * 该方法委托文件引擎实现
     *
     * @param context
     */
    private void storeMultipartFile(FileSaveContext context) {
        try {
            StoreFileContext storeFileContext = new StoreFileContext();
            storeFileContext.setInputStream(context.getFile().getInputStream());
            storeFileContext.setFilename(context.getFilename());
            storeFileContext.setTotalSize(context.getTotalSize());

            storageEngine.store(storeFileContext);
            context.setRealPath(storeFileContext.getRealPath());

        }catch (IOException e){
            e.printStackTrace();
            throw new RPanBusinessException("文件上传失败");
        }
    }

    /**
     * 拼装文件实体对象
     *
     * @param filename
     * @param realPath
     * @param totalSize
     * @param identifier
     * @param userId
     * @return
     */
    private RPanFile asembleRPanFile(String filename, String realPath, Long totalSize, String identifier, Long userId) {
        RPanFile record = new RPanFile();
        record.setFileId(IdUtil.get());
        record.setFilename(filename);
        record.setRealPath(realPath);
        record.setFileSize(String.valueOf(totalSize));
        record.setIdentifier(identifier);
        record.setFileSizeDesc(FileUtil.byteCountToDisplaySize(totalSize));
        record.setIdentifier(identifier);
        record.setCreateUser(userId);
        record.setCreateTime(new Date());
        return record;
    }


}




