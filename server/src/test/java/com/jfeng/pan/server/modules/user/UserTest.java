package com.jfeng.pan.server.modules.user;

import cn.hutool.core.lang.Assert;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.server.RPanServerLauncher;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.service.IUserService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.config.MvcNamespaceHandler;

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
        UserRegisterContext context = createFolderContext();
        Long register = userService.register(context);
        Assert.isTrue(register > 0L);
    }

    /**
     * 测试重复用户名称注册幂等
     */
    @Test(expected = RPanBusinessException.class)
    public void testRegisterDuplicate() {
        UserRegisterContext context = createFolderContext();
        Long register = userService.register(context);
        Assert.isTrue(register > 0L);
        userService.register(context);

    }

    /**
     * 注册用户上下文信息
     * @return
     */
    private UserRegisterContext createFolderContext() {
        UserRegisterContext context = new UserRegisterContext();
        context.setUsername("Jfeng");
        context.setPassword("12345678");
        context.setQuestion("djkasfdughs");
        context.setAnswer("Jfeng");
        return context;
    }
}
