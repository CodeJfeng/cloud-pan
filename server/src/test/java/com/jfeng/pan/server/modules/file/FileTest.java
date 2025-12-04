package com.jfeng.pan.server.modules.file;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.context.QueryFileListContext;
import com.jfeng.pan.server.modules.file.enums.DelFlagEnum;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import com.jfeng.pan.server.modules.user.context.UserLoginContext;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.service.IUserService;
import com.jfeng.pan.server.modules.user.vo.UserInfoVO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文件模块测试用例
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class FileTest {

    @Autowired
    private IUserFileService iUserFileService;

    @Autowired
    private IUserService iUserService;

    /**
     * 测试用户查询文件列表
     */
    @Test
    public void testQueryUserFileListSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        QueryFileListContext context = new QueryFileListContext();
        context.setUserId(userId);
        context.setParentId(userInfoVO.getRootFiled());
        context.setDelFlag(DelFlagEnum.NO.getCode());
        context.setFileTypeArray(null);
        List<RPanUserFileVO> result = iUserFileService.getFileList(context);
        Assert.isTrue(CollectionUtil.isEmpty(result));
    }


    /**
     * 测试创建文件夹成功
     */
    @Test
    public void testCreateFolderSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long folder = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder);
    }

    /********************************* private ************************************/

    /**
     * 用户注册方法
     * @return 新用户Id
     */
    private Long register(){
        UserRegisterContext context = createRegisterContext();
        Long register = iUserService.register(context);
        Assert.isTrue(register > 0L);
        return register;
    }

    /**
     * 查询登录用户的基本信息
     * @return 返回用户的相应信息
     */
    private UserInfoVO info(Long userId){
        UserInfoVO userInfoVO = iUserService.info(userId);
        Assert.notNull(userInfoVO);
        return userInfoVO;
    }


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
