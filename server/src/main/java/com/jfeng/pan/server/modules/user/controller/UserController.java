package com.jfeng.pan.server.modules.user.controller;

import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.converter.UserConverter;
import com.jfeng.pan.server.modules.user.po.UserRegisterPO;
import com.jfeng.pan.server.modules.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
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
     * 用户注册接口
     * <p>
     *     该接口提供了用户注册功能，实现了幂等注册的逻辑，可以放心调用
     * </p>
     *
     * @return 返回加密后的用户ID
     */
    @Operation(summary ="用户注册接口", description = "该接口提供了用户注册功能，实现了幂等注册的逻辑，可以放心调用" )
    @PostMapping("register")
    public R register(@Validated  @RequestBody UserRegisterPO userRegisterPO){
        UserRegisterContext userRegisterContext = userConverter.userRegisterPO2UserRegisterContext(userRegisterPO);
        Long userID = iuserService.register(userRegisterContext);
        return R.data(IdUtil.encrypt(userID));
    }
}
