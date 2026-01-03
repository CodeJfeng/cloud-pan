package com.jfeng.pan.server.modules.share.service;

import com.jfeng.pan.server.modules.share.context.SaveShareFilesContext;
import com.jfeng.pan.server.modules.share.entity.RPanShareFile;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 16837
* @description 针对表【r_pan_share_file(用户分享文件表)】的数据库操作Service
* @createDate 2025-11-06 19:24:38
*/
public interface IShareFileService extends IService<RPanShareFile> {

    /**
     * 保存分享的文件的对应关系
     * @param context
     */
    void saveShareFiles(SaveShareFilesContext context);
}
