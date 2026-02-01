package com.jfeng.pan.server.modules.share.service;

import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import com.jfeng.pan.server.modules.share.context.*;
import com.jfeng.pan.server.modules.share.entity.RPanShare;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jfeng.pan.server.modules.share.vo.ShareDetailVO;
import com.jfeng.pan.server.modules.share.vo.ShareSimpleDetailVO;
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

    /**
     * 查询分享的详情
     * @param context
     * @return
     */
    ShareDetailVO detail(QueryShareDetailContext context);

    /**
     * 查询分享的简单详情
     * @param context
     * @return
     */
    ShareSimpleDetailVO simpleDetail(QueryShareSimpleDetailContext context);

    /**
     * 获取下一级的文件列表
     *
     * @param context
     * @return
     */
    List<RPanUserFileVO> fileList(QueryChildFileListContext context);

    /**
     * 转存至我的网盘
     *
     * @param context
     */
    void saveFiles(ShareSaveContext context);

    /**
     * 分享文件下载
     * @param context
     */
    void download(ShareFileDownloadContext context);

    /**
     * 刷新受影响的对应的分享的状态
     *
     * @param allAvailableFileIdList
     */
    void refreshShareStatus(List<Long> allAvailableFileIdList);

}
