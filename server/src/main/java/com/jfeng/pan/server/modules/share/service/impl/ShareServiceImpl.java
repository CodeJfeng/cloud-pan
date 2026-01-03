package com.jfeng.pan.server.modules.share.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.config.RPanServerConfig;
import com.jfeng.pan.server.modules.share.context.CreateShareUrlContext;
import com.jfeng.pan.server.modules.share.context.QueryShareListContext;
import com.jfeng.pan.server.modules.share.context.SaveShareFilesContext;
import com.jfeng.pan.server.modules.share.entity.RPanShare;
import com.jfeng.pan.server.modules.share.enums.ShareDayTypeEnum;
import com.jfeng.pan.server.modules.share.enums.ShareStatusEnum;
import com.jfeng.pan.server.modules.share.service.IShareFileService;
import com.jfeng.pan.server.modules.share.service.IShareService;
import com.jfeng.pan.server.modules.share.mapper.RPanShareMapper;
import com.jfeng.pan.server.modules.share.vo.ShareUrlListVO;
import com.jfeng.pan.server.modules.share.vo.ShareUrlVO;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
* @author 16837
* @description 针对表【r_pan_share(用户分享表)】的数据库操作Service实现
* @createDate 2025-11-06 19:24:38
*/
@Service
public class ShareServiceImpl extends ServiceImpl<RPanShareMapper, RPanShare>
    implements IShareService {

    @Autowired
    private RPanServerConfig config;

    @Autowired
    private IShareFileService iShareFileService;

    /**
     * 创建文件分享链接
     * 1、拼装分享实体，保存到数据库
     * 2、保存分享和对应文件的关联关系
     * 3、拼装返回实体并返回
     *
     * TODO 该方法缺少对文件Id所有权的校验
     * @param context
     * @return
     */
    @Transactional(rollbackFor = RPanBusinessException.class)
    @Override
    public ShareUrlVO create(CreateShareUrlContext context) {
        saveShare(context);
        saveShareFiles(context);
        return assembleShareVO(context);
    }

    /**
     * 查询用户的分享列表
     *
     * @param context
     * @return
     */
    @Override
    public List<ShareUrlListVO> getShares(QueryShareListContext context) {

        return baseMapper.selectShareVOListByUserId(context.getUserId());
    }

    /******************************************************************* private *****************************************************************************/
    /**
     * 拼装对应的返回VO
     *
     * @param context
     * @return
     */
    private ShareUrlVO assembleShareVO(CreateShareUrlContext context) {
        RPanShare record = context.getRecord();
        ShareUrlVO vo = new ShareUrlVO();
        vo.setShareId(record.getShareId());
        vo.setShareName(record.getShareName());
        vo.setShareUrl(record.getShareUrl());
        vo.setShareCode(record.getShareCode());
        vo.setShareStatus(record.getShareCode());
        return vo;
    }

    /**
     * 保存分享和分享文件的关联关系
     *
     * @param context
     */
    private void saveShareFiles(CreateShareUrlContext context) {
        SaveShareFilesContext saveShareFilesContext = new SaveShareFilesContext();
        saveShareFilesContext.setShareId(context.getRecord().getShareId());
        saveShareFilesContext.setShareFileIdList(context.getShareFileIdList());
        saveShareFilesContext.setUserId(context.getUserId());
        iShareFileService.saveShareFiles(saveShareFilesContext);
    }

    /**
     * 拼装返回的实体，并保存到数据库中
     *
     * @param context
     */
    private void saveShare(CreateShareUrlContext context) {
        RPanShare record = new RPanShare();
        record.setShareId(IdUtil.get());
        record.setShareName(context.getShareName());
        record.setShareType(context.getShareType());
        record.setShareDayType(context.getShareDayType());

        Integer shareDay = ShareDayTypeEnum.getShareDayByCode(context.getShareDayType());
        if(Objects.equals(RPanConstants.MINUS_ONE_INT, shareDay)){
            throw new RPanBusinessException("非法的分享天数");
        }
        record.setShareDay(shareDay);

        record.setShareEndTime(DateUtil.offsetDay(new Date(), shareDay));
        record.setShareUrl(createShareUlr(record.getShareId()));
        record.setShareCode(createShareCode());
        record.setShareStatus(ShareStatusEnum.NORMAL.getCode());
        record.setCreateUser(context.getUserId());
        record.setCreateTime(new Date());

        if (!save(record)){
            throw new RPanBusinessException("保存分享信息失败");
        }
        context.setRecord(record);
    }

    /**
     * 创建分享的分享码
     *
     * @return
     */
    private String createShareCode() {
        return RandomStringUtils.random(4, 'a', 'z', false, false);
    }

    /**
     * 创建分享的URL
     * @param shareId
     * @return
     */
    private String createShareUlr(Long shareId) {
        if(Objects.isNull(shareId)){
            throw new RPanBusinessException("分享的ID不能为空");
        }
        String sharePrefix = config.getSharePrefix();
        if (RPanConstants.SLASH_STR.equals(sharePrefix.charAt(sharePrefix.length() - 1))){
            sharePrefix += RPanConstants.SLASH_STR;
        }
        return sharePrefix+ shareId;

    }
}




