package com.jfeng.pan.server.modules.share.mapper;

import com.jfeng.pan.server.modules.share.entity.RPanShare;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jfeng.pan.server.modules.share.vo.ShareUrlListVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author 16837
* @description 针对表【r_pan_share(用户分享表)】的数据库操作Mapper
* @createDate 2025-11-06 19:24:38
* @Entity com.jfeng.pan.server.modules.share.entity.RPanShare
*/
public interface RPanShareMapper extends BaseMapper<RPanShare> {

    /**
     * 查询用户的分享列表
     *
     * @param userId
     * @return
     */
    List<ShareUrlListVO> selectShareVOListByUserId(@Param("userId") Long userId);
}




