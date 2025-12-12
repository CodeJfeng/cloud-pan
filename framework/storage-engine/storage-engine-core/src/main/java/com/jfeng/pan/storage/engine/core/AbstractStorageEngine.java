package com.jfeng.pan.storage.engine.core;

import cn.hutool.core.lang.Assert;
import com.jfeng.pan.cache.core.constants.CacheConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.storage.engine.core.context.DeleteFileContext;
import com.jfeng.pan.storage.engine.core.context.StoreFileContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.io.IOException;
import java.util.Objects;

/**
 * 顶级文件存储引擎的公用父类
 */
public abstract class AbstractStorageEngine  implements  StorageEngine{

    @Autowired
    private CacheManager cacheManager;

    protected Cache getCache(){
        if(Objects.isNull(cacheManager)){
            throw new RPanBusinessException("this cache manager is empty!");
        }
        return cacheManager.getCache(CacheConstants.R_PAN_CACHE_NAME);
    }

    /**
     * 存储物理文件
     * 1、参数校验
     * 2、执行存储
     * @param context
     * @throws IOException
     */
    @Override
    public void store(StoreFileContext context) throws IOException {
        checkStoreFileContext(context);
        doStore(context);
    }

    /**
     * 执行保存物理文件的动作
     * 下称到具体的子类去实现
     * @param context
     * @throws IOException
     */
    protected abstract void doStore(StoreFileContext context) throws IOException;

    /**
     * 校验上传物理文件的上下文信息
     *
     * @param context
     */
    private void checkStoreFileContext(StoreFileContext context) {
        Assert.notBlank(context.getFilename(), "文件名不能为空");
        Assert.notNull(context.getTotalSize(), "文件总大小不能为空");
        Assert.notNull(context.getInputStream(),"文件不能为空");

    }

    /**
     * 删除物理文件
     * 1、删除校验
     * 2、执行删除
     * @param context
     * @throws IOException
     */
    @Override
    public void delete(DeleteFileContext context) throws IOException {

        checkDeleteFileContext(context);
        doDelete(context);

    }

    /**
     * 执行删除物理文件的动作
     * 下沉到子类去实现
     * @param context
     */
    protected  abstract void doDelete(DeleteFileContext context) throws IOException;

    /**
     * 校验删除物理文件的上下文信息
     * @param context
     */
    private void checkDeleteFileContext(DeleteFileContext context) {
        Assert.notEmpty(context.getRealPathList(),"要删除的文件路径列表不能为空");
    }
}
