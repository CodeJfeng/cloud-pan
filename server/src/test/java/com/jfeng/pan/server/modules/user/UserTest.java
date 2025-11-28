package com.jfeng.pan.server.modules.user;

import cn.hutool.core.lang.Assert;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.JwtUtil;
import com.jfeng.pan.server.RPanServerLauncher;
import com.jfeng.pan.server.modules.user.constants.UserConstants;
import com.jfeng.pan.server.modules.user.context.UserLoginContext;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
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
    private IUserService userService;

    /**
     * 测试成功注册用户信息
     */
    @Test
    public void testRegisterUser() {
        UserRegisterContext context = createRegisterContext();
        Long register = userService.register(context);
        Assert.isTrue(register > 0L);
    }

    /**
     * 测试重复用户名称注册幂等
     */
    @Test(expected = RPanBusinessException.class)
    public void testRegisterDuplicate() {
        UserRegisterContext context = createRegisterContext();
        Long register = userService.register(context);
        Assert.isTrue(register > 0L);
        userService.register(context);

    }

    /**
     * 测试登录成功
     */
    @Test
    public void loginSuccess(){
        UserRegisterContext context = createRegisterContext();
        Long register = userService.register(context);
        Assert.isTrue(register > 0L);

        UserLoginContext loginContext = createLoginContext();
        String token = userService.login(loginContext);
        Assert.isTrue(StringUtils.isNotBlank(token));
    }

    /**
     * 测试登录失败，名称不正确
     */
    @Test(expected = RPanBusinessException.class)
    public void errorName(){
        UserRegisterContext context = createRegisterContext();
        Long register = userService.register(context);
        Assert.isTrue(register > 0L);

        UserLoginContext loginContext = createLoginContext();
        loginContext.setUsername(loginContext.getUsername()+"_err");
        String token = userService.login(loginContext);
    }

    /**
     * 测试登录失败，密码不正确
     */
    @Test(expected = RPanBusinessException.class)
    public void errorPassword(){
        UserRegisterContext context = createRegisterContext();
        Long register = userService.register(context);
        Assert.isTrue(register > 0L);

        UserLoginContext loginContext = createLoginContext();
        loginContext.setPassword(loginContext.getPassword()+"_err");
        String token = userService.login(loginContext);
    }

    /**
     * 用户成功登出单元测试
     */
    @Test
    public void exitSuccess(){
        UserRegisterContext context = createRegisterContext();
        Long register = userService.register(context);
        Assert.isTrue(register > 0L);

        UserLoginContext loginContext = createLoginContext();
        String token = userService.login(loginContext);
        Assert.isTrue(StringUtils.isNotBlank(token));

        Long userId = (Long) JwtUtil.analyzeToken(token, UserConstants.LOGIN_USER_ID);
        userService.exit(userId);
    }

    /********************************* private ************************************/
    private final static String USERNAME = "Jfeng";
    private final static String PASSWORD = "12345678";

    /**
     * 注册用户上下文信息
     * @return
     */
    private UserRegisterContext createRegisterContext() {
        UserRegisterContext context = new UserRegisterContext();
        context.setUsername("Jfeng");
        context.setPassword("12345678");
        context.setQuestion("djkasfdughs");
        context.setAnswer("Jfeng");
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
