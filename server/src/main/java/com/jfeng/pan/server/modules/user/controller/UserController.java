package com.jfeng.pan.server.modules.user.controller;

import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.utils.UserIdUtil;
import com.jfeng.pan.server.modules.user.context.UserLoginContext;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.converter.UserConverter;
import com.jfeng.pan.server.modules.user.po.UserLoginPO;
import com.jfeng.pan.server.modules.user.po.UserRegisterPO;
import com.jfeng.pan.server.modules.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户模块的控制器实体
 */
@RestController
@RequestMapping("user")
@Tag(name = "用户接口")
public class UserController {

    @Autowired
    private IUserService iuserService;

    @Autowired
    public UserConverter userConverter;

    /**
     * <p>
     *     用户注册接口
     *     该接口提供了用户注册功能，实现了幂等注册的逻辑，可以放心调用
     * </p>
     *
     * @return 返回加密后的用户ID
     */
    @Operation(summary ="用户注册接口",
            description = "提供用户登录接口，实现了幂等注册的逻辑，可以放心调用"
    )
    @PostMapping("register")
    public R register(@Validated  @RequestBody UserRegisterPO userRegisterPO){
        UserRegisterContext userRegisterContext = userConverter.userRegisterPO2UserRegisterContext(userRegisterPO);
        Long userID = iuserService.register(userRegisterContext);
        return R.data(IdUtil.encrypt(userID));
    }

    /**
     * <p>
     *     用户登录接口
     *     该接口提供了用户登录功能，成功登录以后，会返回具有时效性的accessToken
     * </p>
     *
     * @return 返回加密后的用户ID
     */
    @Operation(summary ="用户登录接口",
            description = "该接口提供了用户登录功能，成功登录以后，会返回具有时效性的accessToken",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "注册成功"
                    )
    })
    @PostMapping("login")
    public R login(@Validated  @RequestBody UserLoginPO userLoginPO){
        UserLoginContext userLoginContext = userConverter.userLoginPO2RPanLoginContext(userLoginPO);
        String token = iuserService.login(userLoginContext);
        return R.data(token);
    }

    /**
     * <p>
     *     用户登出接口
     *     该接口提供了用户退出登录的功能，
     * </p>
     *
     * @return 返回退出成功信息
     */
    @Operation(summary ="用户登出接口",
            description = "该接口提供了用户登出账号的功能"
    )
    @PostMapping("exit")
    public R exit(){
        iuserService.exit(UserIdUtil.get());
        return R.success();
    }
}
