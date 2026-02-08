package com.jfeng.pan.server.modules.share;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Assert;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.context.FileChunkMergeContext;
import com.jfeng.pan.server.modules.file.context.FileChunkUploadContext;
import com.jfeng.pan.server.modules.file.context.QueryFileListContext;
import com.jfeng.pan.server.modules.file.enums.MergeFlagEnum;
import com.jfeng.pan.server.modules.file.service.IFileChunkService;
import com.jfeng.pan.server.modules.file.service.IFileService;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.vo.FileChunkUploadVO;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import com.jfeng.pan.server.modules.share.context.*;
import com.jfeng.pan.server.modules.share.enums.ShareDayTypeEnum;
import com.jfeng.pan.server.modules.share.enums.ShareTypeEnum;
import com.jfeng.pan.server.modules.share.service.IShareService;
import com.jfeng.pan.server.modules.share.vo.ShareDetailVO;
import com.jfeng.pan.server.modules.share.vo.ShareSimpleDetailVO;
import com.jfeng.pan.server.modules.share.vo.ShareUrlListVO;
import com.jfeng.pan.server.modules.share.vo.ShareUrlVO;
import com.jfeng.pan.server.modules.user.context.UserLoginContext;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.service.IUserService;
import com.jfeng.pan.server.modules.user.vo.UserInfoVO;
import lombok.AllArgsConstructor;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * 文件分享模块单元测试类
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest
//@Transactional
public class ShareTest {

    @Autowired
    private IUserFileService iUserFileService;

    @Autowired
    private IUserService iUserService;


    @Autowired
    private IFileService iFileService;

    @Autowired
    private IFileChunkService iFileChunkService;

    @Autowired
    private IShareService iShareService;

    /**
     * 创建分享链接成功
     */
    @Test
    public void createShareUrlSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        // 创建文件夹
        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        // 创建分享的URL链接
        CreateShareUrlContext createShareUrlContext = new CreateShareUrlContext();
        createShareUrlContext.setShareName("share-1");
        createShareUrlContext.setShareDayType(ShareDayTypeEnum.SEVEN_DAY_VALIDITY.getCode());
        createShareUrlContext.setShareType(ShareTypeEnum.NEED_SHARE_CODE.getCode());
        createShareUrlContext.setUserId(userId);
        createShareUrlContext.setShareFileIdList(Lists.newArrayList(fileId));
        ShareUrlVO shareUrlVO = iShareService.create(createShareUrlContext);
        Assert.isTrue(Objects.nonNull(shareUrlVO));
    }


    /**
     * 查询分享链接列表成功
     */
    @Test
    public void queryShareUrlListSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        // 创建文件夹
        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        // 创建分享的URL链接
        CreateShareUrlContext createShareUrlContext = new CreateShareUrlContext();
        createShareUrlContext.setShareName("share-1");
        createShareUrlContext.setShareDayType(ShareDayTypeEnum.SEVEN_DAY_VALIDITY.getCode());
        createShareUrlContext.setShareType(ShareTypeEnum.NEED_SHARE_CODE.getCode());
        createShareUrlContext.setUserId(userId);
        createShareUrlContext.setShareFileIdList(Lists.newArrayList(fileId));
        ShareUrlVO shareUrlVO = iShareService.create(createShareUrlContext);
        Assert.isTrue(Objects.nonNull(shareUrlVO));

        // 查询分享链接列表
        QueryShareListContext queryShareListContext = new QueryShareListContext();
        queryShareListContext.setUserId(userId);
        List<ShareUrlListVO> result = iShareService.getShares(queryShareListContext);
        Assert.notEmpty(result);
    }

    /**
     * 取消分享链接成功
     */
    @Test
    public void cancelShareUrlSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        // 创建文件夹
        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        // 创建分享的URL链接
        CreateShareUrlContext createShareUrlContext = new CreateShareUrlContext();
        createShareUrlContext.setShareName("share-1");
        createShareUrlContext.setShareDayType(ShareDayTypeEnum.SEVEN_DAY_VALIDITY.getCode());
        createShareUrlContext.setShareType(ShareTypeEnum.NEED_SHARE_CODE.getCode());
        createShareUrlContext.setUserId(userId);
        createShareUrlContext.setShareFileIdList(Lists.newArrayList(fileId));
        ShareUrlVO shareUrlVO = iShareService.create(createShareUrlContext);
        Assert.isTrue(Objects.nonNull(shareUrlVO));

        // 查询分享链接列表
        QueryShareListContext queryShareListContext = new QueryShareListContext();
        queryShareListContext.setUserId(userId);
        List<ShareUrlListVO> result = iShareService.getShares(queryShareListContext);
        Assert.notEmpty(result);

        // 取消分享列表
        CancelShareContext cancelShareContext = new CancelShareContext();
        cancelShareContext.setUserId(userId);
        cancelShareContext.setShareIdList(Lists.newArrayList(shareUrlVO.getShareId()));
        iShareService.cancelShare(cancelShareContext);

        // 查询分享链接列表
        result = iShareService.getShares(queryShareListContext);
        Assert.isTrue(CollectionUtil.isEmpty(result));
    }

    /**
     * 校验分享码成功
     */
    @Test
    public void checkShareCodeSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        // 创建文件夹
        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        // 创建分享的URL链接
        CreateShareUrlContext createShareUrlContext = new CreateShareUrlContext();
        createShareUrlContext.setShareName("share-1");
        createShareUrlContext.setShareDayType(ShareDayTypeEnum.SEVEN_DAY_VALIDITY.getCode());
        createShareUrlContext.setShareType(ShareTypeEnum.NEED_SHARE_CODE.getCode());
        createShareUrlContext.setUserId(userId);
        createShareUrlContext.setShareFileIdList(Lists.newArrayList(fileId));
        ShareUrlVO shareUrlVO = iShareService.create(createShareUrlContext);
        Assert.isTrue(Objects.nonNull(shareUrlVO));

        // 校验文件的分享码
        CheckShareCodeContext checkShareCodeContext = new CheckShareCodeContext();
        checkShareCodeContext.setShareId(shareUrlVO.getShareId());
        checkShareCodeContext.setShareCode(shareUrlVO.getShareCode());
        String token = iShareService.checkShareCode(checkShareCodeContext);
        Assert.notBlank(token);
    }

    /**
     * 校验分享码失败——分享码错误
     */
    @Test(expected = RPanBusinessException.class)
    public void checkShareCodeFail(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        // 创建文件夹
        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        // 创建分享的URL链接
        CreateShareUrlContext createShareUrlContext = new CreateShareUrlContext();
        createShareUrlContext.setShareName("share-1");
        createShareUrlContext.setShareDayType(ShareDayTypeEnum.SEVEN_DAY_VALIDITY.getCode());
        createShareUrlContext.setShareType(ShareTypeEnum.NEED_SHARE_CODE.getCode());
        createShareUrlContext.setUserId(userId);
        createShareUrlContext.setShareFileIdList(Lists.newArrayList(fileId));
        ShareUrlVO shareUrlVO = iShareService.create(createShareUrlContext);
        Assert.isTrue(Objects.nonNull(shareUrlVO));

        // 校验文件的分享码
        CheckShareCodeContext checkShareCodeContext = new CheckShareCodeContext();
        checkShareCodeContext.setShareId(shareUrlVO.getShareId());
        checkShareCodeContext.setShareCode(shareUrlVO.getShareCode() + "_change");
        String token = iShareService.checkShareCode(checkShareCodeContext);
        Assert.notBlank(token);
    }


    /**
     * 校验查询分享详情成功
     */
    @Test
    public void queryShareDetailSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        // 创建文件夹
        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        // 创建分享的URL链接
        CreateShareUrlContext createShareUrlContext = new CreateShareUrlContext();
        createShareUrlContext.setShareName("share-1");
        createShareUrlContext.setShareDayType(ShareDayTypeEnum.SEVEN_DAY_VALIDITY.getCode());
        createShareUrlContext.setShareType(ShareTypeEnum.NEED_SHARE_CODE.getCode());
        createShareUrlContext.setUserId(userId);
        createShareUrlContext.setShareFileIdList(Lists.newArrayList(fileId));
        ShareUrlVO shareUrlVO = iShareService.create(createShareUrlContext);
        Assert.isTrue(Objects.nonNull(shareUrlVO));

        // 查询分享详情
        QueryShareDetailContext queryShareDetailContext = new QueryShareDetailContext();
        queryShareDetailContext.setShareId(shareUrlVO.getShareId());
        ShareDetailVO shareDetailVO = iShareService.detail(queryShareDetailContext);
        Assert.notNull(shareDetailVO);
        System.out.println(shareDetailVO);
    }

    /**
     * 校验查询分享简单详情成功
     */
    @Test
    public void queryShareSimpleDetailSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        // 创建文件夹
        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        // 创建分享的URL链接
        CreateShareUrlContext createShareUrlContext = new CreateShareUrlContext();
        createShareUrlContext.setShareName("share-1");
        createShareUrlContext.setShareDayType(ShareDayTypeEnum.SEVEN_DAY_VALIDITY.getCode());
        createShareUrlContext.setShareType(ShareTypeEnum.NEED_SHARE_CODE.getCode());
        createShareUrlContext.setUserId(userId);
        createShareUrlContext.setShareFileIdList(Lists.newArrayList(fileId));
        ShareUrlVO shareUrlVO = iShareService.create(createShareUrlContext);
        Assert.isTrue(Objects.nonNull(shareUrlVO));

        // 查询分享的简单详情
        QueryShareSimpleDetailContext queryShareSimpleDetailContext = new QueryShareSimpleDetailContext();
        queryShareSimpleDetailContext.setShareId(shareUrlVO.getShareId());
        ShareSimpleDetailVO shareSimpleDetailVO = iShareService.simpleDetail(queryShareSimpleDetailContext);
        Assert.notNull(shareSimpleDetailVO);
        System.out.println(shareSimpleDetailVO);
    }

    /**
     * 校验查询分享下一级文件列表成功
     */
    @Test
    public void queryShareFileListSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        // 创建文件夹
        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        // 创建分享的URL链接
        CreateShareUrlContext createShareUrlContext = new CreateShareUrlContext();
        createShareUrlContext.setShareName("share-1");
        createShareUrlContext.setShareDayType(ShareDayTypeEnum.SEVEN_DAY_VALIDITY.getCode());
        createShareUrlContext.setShareType(ShareTypeEnum.NEED_SHARE_CODE.getCode());
        createShareUrlContext.setUserId(userId);
        createShareUrlContext.setShareFileIdList(Lists.newArrayList(userInfoVO.getRootFiled()));
        ShareUrlVO shareUrlVO = iShareService.create(createShareUrlContext);
        Assert.isTrue(Objects.nonNull(shareUrlVO));

        // 查询分享的下一级文件夹
        QueryChildFileListContext queryChildFileListContext = new QueryChildFileListContext();
        queryChildFileListContext.setShareId(shareUrlVO.getShareId());
        queryChildFileListContext.setParentId(userInfoVO.getRootFiled());
        List<RPanUserFileVO> fileVOList = iShareService.fileList(queryChildFileListContext);


        Assert.isTrue(CollectionUtil.isNotEmpty(fileVOList));
    }


    @Test
    public void init(){
        CreateShareUrlContext context = new CreateShareUrlContext();
        context.setUserId(2019781914771701760L);
        context.setShareType(ShareTypeEnum.NEED_SHARE_CODE.getCode());
        context.setShareDayType(ShareDayTypeEnum.SEVEN_DAY_VALIDITY.getCode());
        context.setShareFileIdList(Lists.newArrayList(2019791281575280640L));
        for (int i = 0; i < 2; i++){
            context.setShareName("测试分享"+ i);
            iShareService.create(context);
        }
    }

    /************************************************************* private ****************************************************************/


    /**
     * 生成的网络文件实体
     * @return
     */
    private static MultipartFile generateMultipartFile() {
        MultipartFile file = null;
        try {
            StringBuffer sb = new StringBuffer();
            for(int i = 0; i < 1024 * 1024; i++ ){
                sb.append("A");
            }
            file = new MockMultipartFile("file", "test.txt", "multipart/form-data", sb.toString().getBytes(StandardCharsets.UTF_8));
        }catch (Exception e){
            e.printStackTrace();
        }
        return file;
    }


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
