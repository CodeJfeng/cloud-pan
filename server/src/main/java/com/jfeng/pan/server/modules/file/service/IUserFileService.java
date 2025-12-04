package com.jfeng.pan.server.modules.file.service;

import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.context.QueryFileListContext;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;

import java.util.List;

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

    /**
     * 查询用户的根文件夹信息
     *
     * @param userId
     * @return
     */
    RPanUserFile getUserRootFile(Long userId);

    /**
     * 查询用户的文件列表
     *
     * @param queryFileListContext
     * @return
     */
    List<RPanUserFileVO> getFileList(QueryFileListContext queryFileListContext);
}
