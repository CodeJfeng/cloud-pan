package com.jfeng.pan.server.modules.share.service;

import com.jfeng.pan.server.modules.share.context.CancelShareContext;
import com.jfeng.pan.server.modules.share.context.CheckShareCodeContext;
import com.jfeng.pan.server.modules.share.context.CreateShareUrlContext;
import com.jfeng.pan.server.modules.share.context.QueryShareListContext;
import com.jfeng.pan.server.modules.share.entity.RPanShare;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jfeng.pan.server.modules.share.vo.ShareUrlListVO;
import com.jfeng.pan.server.modules.share.vo.ShareUrlVO;

import java.util.List;

/**
* @author 16837
* @description 针对表【r_pan_share(用户分享表)】的数据库操作Service
* @createDate 2025-11-06 19:24:38
*/
public interface IShareService extends IService<RPanShare> {

    /**
     * 创建文件分享链接
     *
     * @param context
     * @return
     */
    ShareUrlVO create(CreateShareUrlContext context);

    /**
     * 查询用户的分享列表
     *
     * @param shareListContext
     * @return
     */
    List<ShareUrlListVO> getShares(QueryShareListContext shareListContext);

    /**
     * 取消分享链接
     *
     * @param context
     */
    void cancelShare(CancelShareContext context);

    /**
     * 校验分享码
     *
     * @param context
     * @return
     */
    String checkShareCode(CheckShareCodeContext context);
}
