package com.jfeng.pan.server.modules.file.service;

import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 16837
* @description 针对表【r_pan_user_file(用户文件信息表)】的数据库操作Service
* @createDate 2025-11-06 19:22:58
*/
public interface IUserFileService extends IService<RPanUserFile> {
    /**
     * 创建文件夹信息
     * @param context
     * @return
     */
    Long createFolder(CreateFolderContext context);
}
