package com.jfeng.pan.server.modules.share.service;

import com.jfeng.pan.server.modules.share.context.CreateShareUrlContext;
import com.jfeng.pan.server.modules.share.entity.RPanShare;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jfeng.pan.server.modules.share.vo.ShareUrlVO;

/**
* @author 16837
* @description 针对表【r_pan_share(用户分享表)】的数据库操作Service
* @createDate 2025-11-06 19:24:38
*/
public interface IShareService extends IService<RPanShare> {

    /**
     * 创建文件分享链接
     * @param context
     * @return
     */
    ShareUrlVO create(CreateShareUrlContext context);
}
