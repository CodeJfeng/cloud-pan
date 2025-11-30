package com.jfeng.pan.server.modules.user.service;

import com.jfeng.pan.server.modules.user.context.*;
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

    /**
     * 用户退出登录业务接口
     * @param userId 用户id
     */
    void exit(Long userId);

    /**
     * 用户忘记密码-校验用户名业务接口
     * @param checkUsernameContext 校验用户上下文
     * @return 返回密码问题
     */
    String checkUsername(CheckUsernameContext checkUsernameContext);

    /**
     * 用户忘记密码-校验密保答案
     * @param checkAnswerContext 校验密保问题上下文
     * @return 返回token
     */
    String checkAnswer(CheckAnswerContext checkAnswerContext);

    void resetPassword(ResetPasswordContext resetPasswordContext);
}
