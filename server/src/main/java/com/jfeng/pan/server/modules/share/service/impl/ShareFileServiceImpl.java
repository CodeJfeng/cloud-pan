package com.jfeng.pan.server.modules.share.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.modules.share.context.SaveShareFilesContext;
import com.jfeng.pan.server.modules.share.entity.RPanShareFile;
import com.jfeng.pan.server.modules.share.service.IShareFileService;
import com.jfeng.pan.server.modules.share.mapper.RPanShareFileMapper;
import org.assertj.core.util.Lists;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
* @author 16837
* @description 针对表【r_pan_share_file(用户分享文件表)】的数据库操作Service实现
* @createDate 2025-11-06 19:24:38
*/
@Service
public class ShareFileServiceImpl extends ServiceImpl<RPanShareFileMapper, RPanShareFile>
    implements IShareFileService {

    /**
     * 保存分享的文件的对应关系
     *
     * @param context
     */
    @Override
    public void saveShareFiles(SaveShareFilesContext context) {
        Long shareId = context.getShareId();
        List<Long> shareFileIdList = context.getShareFileIdList();
        Long userId = context.getUserId();
        List<RPanShareFile> records = Lists.newArrayList();
        for (Long shareFileId : shareFileIdList) {
            RPanShareFile record = new RPanShareFile();
            record.setId(IdUtil.get());
            record.setShareId(shareId);
            record.setFileId(shareFileId);
            record.setCreateUser(userId);
            record.setCreateTime(new Date());
            records.add(record);
        }
        if(!saveBatch(records)){
            throw new RPanBusinessException("保存文件分享关联关系失败");
        }
    }
}




