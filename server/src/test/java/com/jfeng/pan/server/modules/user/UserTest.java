package com.jfeng.pan.server.modules.user;

import cn.hutool.core.lang.Assert;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.JwtUtil;
import com.jfeng.pan.server.RPanServerLauncher;
import com.jfeng.pan.server.modules.user.constants.UserConstants;
import com.jfeng.pan.server.modules.user.context.*;
import com.jfeng.pan.server.modules.user.service.IUserService;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户模块单元测试类
 */
@RunWith(SpringJUnit4ClassRunner.class)
@Transactional
@SpringBootTest(classes = {RPanServerLauncher.class})
public class UserTest {

    @Autowired
    private IUserService iuserService;

    /**
     * 测试成功注册用户信息
     */
    @Test
    public void testRegisterUser() {
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);
    }

    /**
     * 测试重复用户名称注册幂等
     */
    @Test(expected = RPanBusinessException.class)
    public void testRegisterDuplicate() {
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);
        iuserService.register(context);

    }

    /**
     * 测试登录成功
     */
    @Test
    public void loginSuccess(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        UserLoginContext loginContext = createLoginContext();
        String token = iuserService.login(loginContext);
        Assert.isTrue(StringUtils.isNotBlank(token));
    }

    /**
     * 测试登录失败，名称不正确
     */
    @Test(expected = RPanBusinessException.class)
    public void errorName(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        UserLoginContext loginContext = createLoginContext();
        loginContext.setUsername(loginContext.getUsername()+"_err");
        String token = iuserService.login(loginContext);
    }

    /**
     * 测试登录失败，密码不正确
     */
    @Test(expected = RPanBusinessException.class)
    public void errorPassword(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        UserLoginContext loginContext = createLoginContext();
        loginContext.setPassword(loginContext.getPassword()+"_err");
        String token = iuserService.login(loginContext);
    }

    /**
     * 用户成功登出单元测试
     */
    @Test
    public void exitSuccess(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        UserLoginContext loginContext = createLoginContext();
        String token = iuserService.login(loginContext);
        Assert.isTrue(StringUtils.isNotBlank(token));

        Long userId = (Long) JwtUtil.analyzeToken(token, UserConstants.LOGIN_USER_ID);
        iuserService.exit(userId);
    }

    /**
     * 校验用户名称成功
     */
    @Test
    public void checkUsernameSuccess(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        CheckUsernameContext checkUsernameContext = new CheckUsernameContext();
        checkUsernameContext.setUsername(context.getUsername());

        String question = iuserService.checkUsername(checkUsernameContext);
        Assert.isTrue(StringUtils.isNotBlank(question));
    }


    /**
     * 校验用户名称失败——没有查询到用户信息
     */
    @Test(expected = RPanBusinessException.class)
    public void checkUsernameFail(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        CheckUsernameContext checkUsernameContext = new CheckUsernameContext();
        checkUsernameContext.setUsername(context.getUsername() + "_err" );

        String question = iuserService.checkUsername(checkUsernameContext);

    }


    /**
     * 校验用户密保问题答案通过
     */
    @Test
    public void checkAnswerSuccess(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        CheckUsernameContext checkUsernameContext = new CheckUsernameContext();
        checkUsernameContext.setUsername(context.getUsername());
        String question = iuserService.checkUsername(checkUsernameContext);

        CheckAnswerContext checkAnswerContext = new CheckAnswerContext();
        checkAnswerContext.setUsername(context.getUsername());
        checkAnswerContext.setQuestion(question);
        checkAnswerContext.setAnswer(context.getAnswer());
        String token = iuserService.checkAnswer(checkAnswerContext);
        Assert.isTrue(StringUtils.isNotBlank(token));
    }

    /**
     * 校验用户密保问题答案失败
     */
    @Test(expected = RPanBusinessException.class)
    public void checkAnswerFail(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        CheckUsernameContext checkUsernameContext = new CheckUsernameContext();
        checkUsernameContext.setUsername(context.getUsername());
        String question = iuserService.checkUsername(checkUsernameContext);

        CheckAnswerContext checkAnswerContext = new CheckAnswerContext();
        checkAnswerContext.setUsername(context.getUsername());
        checkAnswerContext.setQuestion(question);
        checkAnswerContext.setAnswer(context.getAnswer()+"_err");
        String token = iuserService.checkAnswer(checkAnswerContext);
        Assert.isTrue(StringUtils.isNotBlank(token));
    }

    /**
     * 用户正常重置密码
     */
    @Test
    public void resetPasswordSuccess(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        CheckUsernameContext checkUsernameContext = new CheckUsernameContext();
        checkUsernameContext.setUsername(context.getUsername());
        String question = iuserService.checkUsername(checkUsernameContext);

        CheckAnswerContext checkAnswerContext = new CheckAnswerContext();
        checkAnswerContext.setUsername(context.getUsername());
        checkAnswerContext.setQuestion(question);
        checkAnswerContext.setAnswer(context.getAnswer());
        String token = iuserService.checkAnswer(checkAnswerContext);

        ResetPasswordContext resetPasswordContext = new ResetPasswordContext();
        resetPasswordContext.setToken(token);
        resetPasswordContext.setUsername(context.getUsername());
        resetPasswordContext.setPassword(context.getPassword()+"_new");
        iuserService.resetPassword(resetPasswordContext);
    }

    /**
     * 用户重置密码失败，token过期返回异常
     */
    @Test(expected = RPanBusinessException.class)
    public void resetPasswordTokenFail(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        CheckUsernameContext checkUsernameContext = new CheckUsernameContext();
        checkUsernameContext.setUsername(context.getUsername());
        String question = iuserService.checkUsername(checkUsernameContext);

        CheckAnswerContext checkAnswerContext = new CheckAnswerContext();
        checkAnswerContext.setUsername(context.getUsername());
        checkAnswerContext.setQuestion(question);
        checkAnswerContext.setAnswer(context.getAnswer());
        String token = iuserService.checkAnswer(checkAnswerContext);

        ResetPasswordContext resetPasswordContext = new ResetPasswordContext();
        resetPasswordContext.setToken(token+"_err");
        resetPasswordContext.setUsername(context.getUsername());
        resetPasswordContext.setPassword(context.getPassword()+"_new");
        iuserService.resetPassword(resetPasswordContext);
    }

    /**
     * 正常在线修改新密码
     */
    @Test
    public void changePasswordSuccess(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        ChangePasswordContext changePasswordContext = new ChangePasswordContext();
        changePasswordContext.setUserId(register);
        changePasswordContext.setOldPassword(PASSWORD);
        changePasswordContext.setNewPassword(PASSWORD+"_new");

        iuserService.changePassword(changePasswordContext);
    }

    /**
     * 修改密码失败--与旧密码错误
     */
    @Test(expected = RPanBusinessException.class)
    public void changePasswordFailByWrongOldPassword(){
        UserRegisterContext context = createRegisterContext();
        Long register = iuserService.register(context);
        Assert.isTrue(register > 0L);

        ChangePasswordContext changePasswordContext = new ChangePasswordContext();
        changePasswordContext.setUserId(register);
        changePasswordContext.setOldPassword(PASSWORD+ "_err");
        changePasswordContext.setNewPassword(PASSWORD+ "_new");

        iuserService.changePassword(changePasswordContext);

    }
    /********************************* private ************************************/


    private final String USERNAME = "Jfeng";
    private final String PASSWORD = "12345678";
    private final String QUESTION = "Question123";
    private final String ANSWER = "Answer123";


    /**
     * 注册用户上下文信息
     * @return
     */
    private UserRegisterContext createRegisterContext() {
        UserRegisterContext context = new UserRegisterContext();
        context.setUsername(USERNAME);
        context.setPassword(PASSWORD);
        context.setQuestion(QUESTION);
        context.setAnswer(ANSWER);
        return context;
    }
    /**
     * 构建用户登录上下文信息
     * @return
     */
    private UserLoginContext createLoginContext() {
        UserLoginContext userLoginContext = new UserLoginContext();
        userLoginContext.setUsername(USERNAME);
        userLoginContext.setPassword(PASSWORD);
        return userLoginContext;
    }
}
