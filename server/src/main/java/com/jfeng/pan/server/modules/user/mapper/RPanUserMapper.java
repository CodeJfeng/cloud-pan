package com.jfeng.pan.server.modules.user.mapper;

import com.jfeng.pan.server.modules.user.entity.RPanUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
* @author 16837
* @description 针对表【r_pan_user(用户信息表)】的数据库操作Mapper
* @createDate 2025-11-06 19:14:11
* @Entity com.jfeng.pan.server.modules.user.entity.RPanUser
*/
public interface RPanUserMapper extends BaseMapper<RPanUser> {

    /**
     * <p>
     *     通过username查询用户设置的密保问题
     * </p>
     * @param username 用户名称
     * @return 密保问题
     */
    String selectQuestionByUsername(@Param("username") String username);
}




