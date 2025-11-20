package com.jfeng.pan.server.modules.user.service;

import com.jfeng.pan.server.modules.user.context.UserLoginContext;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.entity.RPanUser;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 16837
* @description 针对表【r_pan_user(用户信息表)】的数据库操作Service
* @createDate 2025-11-06 19:14:11
*/
public interface IUserService extends IService<RPanUser> {

    /**
     * 用户注册业务
     * @param userRegisterContext 用户信息
     * @return 用户加密的userID
     */
    Long register(UserRegisterContext userRegisterContext);

    /**
     * 用户登录接口
     * @param userLoginContext 用户信息
     * @return 带过期时间的token
     */
    String login(UserLoginContext userLoginContext);
}
