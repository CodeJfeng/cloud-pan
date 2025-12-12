package com.jfeng.pan.server.modules.file.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.FileUtil;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.event.log.ErrorLogEvent;
import com.jfeng.pan.server.modules.file.context.FileSaveContext;
import com.jfeng.pan.server.modules.file.entity.RPanFile;
import com.jfeng.pan.server.modules.file.service.IFileService;
import com.jfeng.pan.server.modules.file.mapper.RPanFileMapper;
import com.jfeng.pan.storage.engine.core.StorageEngine;
import com.jfeng.pan.storage.engine.core.context.DeleteFileContext;
import com.jfeng.pan.storage.engine.core.context.StoreFileContext;
import org.assertj.core.util.Lists;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Date;

/**
* @author 16837
* @description 针对表【r_pan_file(物理文件信息表)】的数据库操作Service实现
* @createDate 2025-11-06 19:22:58
*/
@Service(value = "userFileService")
public class FileServiceImpl extends ServiceImpl<RPanFileMapper, RPanFile> implements IFileService, ApplicationContextAware {

    @Autowired
    private StorageEngine storageEngine;

    @Autowired
    private ApplicationContext applicationContext;

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
     * 广播机器出错的事件
     * @param applicationContext the ApplicationContext object to be used by this object
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

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




