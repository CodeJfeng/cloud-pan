package com.jfeng.pan.server.modules.user.controller;

import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.annotation.LoginIgnore;
import com.jfeng.pan.server.common.utils.UserIdUtil;
import com.jfeng.pan.server.modules.user.context.*;
import com.jfeng.pan.server.modules.user.converter.UserConverter;
import com.jfeng.pan.server.modules.user.po.*;
import com.jfeng.pan.server.modules.user.service.IUserService;
import com.jfeng.pan.server.modules.user.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    @LoginIgnore
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
    @LoginIgnore
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

    /**
     * <p>
     *     用户忘记密码--校验用户名称
     *     该接口提供了用户在忘记密码时，验证用户名是否存在
     * </p>
     *
     * @return 返回用户名校验信息，以及后续的密保问题
     */
    @Operation(summary ="用户校验密码--校验用户名称",
            description = "该接口提供了用户在忘记密码时，验证用户名是否存在"
    )
    @PostMapping("username/check")
    public R checkUsername(@Validated @RequestBody CheckUsernamePO checkUsernamePO){
        CheckUsernameContext checkUsernameContext = userConverter.checkUsernamePO2CheckUsernameContext(checkUsernamePO);
        String question = iuserService.checkUsername(checkUsernameContext);
        return R.data(question);
    }

    /**
     * <p>
     *     用户忘记密码--校验密保答案
     *     该接口提供了用户在忘记密码时，验证校验密保答案是否正确
     * </p>
     *
     * @return 返回密保答案校验结果
     */
    @Operation(summary ="用户忘记密码--校验密保答案",
            description = "该接口提供了用户在忘记密码时，验证校验密保答案是否正确"
    )
    @PostMapping("question/check")
    public R checkAnswer(@Validated @RequestBody CheckAnswerPO checkAnswerPO){
        CheckAnswerContext checkAnswerContext = userConverter.checkAnswerPO2CheckAnswerContext(checkAnswerPO);
        String token = iuserService.checkAnswer(checkAnswerContext);
        return R.data(token);
    }

    /**
     * <p>
     *     用户忘记密码--更新密码
     *     该接口提供了用户在忘记密码时，通过token去更新密码信息
     * </p>
     *
     * @return
     */
    @Operation(summary ="用户忘记密码--更新密码",
            description = "该接口提供了用户在忘记密码时，通过token去更新密码信息"
    )
    @PostMapping("password/reset")
    public R resetPassword(@Validated @RequestBody ResetPasswordPO resetPasswordPO){
        ResetPasswordContext resetPasswordContext = userConverter.resetPasswordPO2ResetPasswordContext(resetPasswordPO);
        iuserService.resetPassword(resetPasswordContext);
        return R.success();
    }

    /**
     * <p>
     *     用户在线更新密码
     *     该接口提供了用户在线更新密码
     * </p>
     *
     * @return
     */
    @Operation(summary ="用户在线更新密码",
            description = "该接口提供了用户在线更新密码"
    )
    @PostMapping("password/change")
    public R changePassword(@Validated @RequestBody ChangePasswordPO changePasswordPO){
        ChangePasswordContext  changePasswordContext = userConverter.changePasswordPO2ChangePasswordContext(changePasswordPO);
        changePasswordContext.setUserId(UserIdUtil.get());
        iuserService.changePassword(changePasswordContext);
        return R.success();
    }

    /**
     * <p>
     *     提供了查询用户的基本信息接口
     * </p>
     * @return
     */
    @Operation(summary ="查询用户的基本信息",
            description = "该接口提供了查询用户的基本信息"
    )
    @GetMapping("/")
    public R<UserInfoVO> info(){
        UserInfoVO userInfoVO = iuserService.info(UserIdUtil.get());
        return R.data(userInfoVO);
    }
}
