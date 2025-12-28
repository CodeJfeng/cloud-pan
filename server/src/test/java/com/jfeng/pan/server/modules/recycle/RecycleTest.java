package com.jfeng.pan.server.modules.recycle;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.context.DeleteFileContext;
import com.jfeng.pan.server.modules.file.service.IFileService;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import com.jfeng.pan.server.modules.recycle.context.QueryRecycleFileListContext;
import com.jfeng.pan.server.modules.recycle.service.IRecycleService;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.service.IUserService;
import com.jfeng.pan.server.modules.user.vo.UserInfoVO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 回收站模块测试用例
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class RecycleTest {
    @Autowired
    private IUserFileService iUserFileService;

    @Autowired
    private IUserService iUserService;


    @Autowired
    private IFileService iFileService;

    @Autowired
    private IRecycleService iRecycleService;


    /**
     * 测试查询回收站文件列表成功
     */
    @Test
    public void testQueryRecyclesSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        // 创建一个文件夹
        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        // 删除该文件
        DeleteFileContext deleteFileContext = new DeleteFileContext();
        List<Long> fileIdList = new ArrayList<>();
        fileIdList.add(fileId);
        deleteFileContext.setFileIdList(fileIdList);
        deleteFileContext.setUserId(userId);
        iUserFileService.deleteFile(deleteFileContext);

        // 查询回收站列表，校验列表长度
        QueryRecycleFileListContext queryRecycleFileListContext = new QueryRecycleFileListContext();
        queryRecycleFileListContext.setUserId(userId);
        List<RPanUserFileVO> recycles = iRecycleService.recycles(queryRecycleFileListContext);
        Assert.isTrue(CollectionUtil.isNotEmpty(recycles) );
        Assert.isTrue(recycles.size() == 1);
    }

    /**************************************** private **************************************************/

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

}
