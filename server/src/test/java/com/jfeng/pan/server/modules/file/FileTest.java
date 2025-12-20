package com.jfeng.pan.server.modules.file;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.modules.file.context.*;
import com.jfeng.pan.server.modules.file.entity.RPanFile;
import com.jfeng.pan.server.modules.file.entity.RPanFileChunk;
import com.jfeng.pan.server.modules.file.enums.DelFlagEnum;
import com.jfeng.pan.server.modules.file.enums.MergeFlagEnum;
import com.jfeng.pan.server.modules.file.service.IFileChunkService;
import com.jfeng.pan.server.modules.file.service.IFileService;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.file.vo.FileChunkUploadVO;
import com.jfeng.pan.server.modules.file.vo.FolderTreeNodeVO;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import com.jfeng.pan.server.modules.file.vo.UploadedChunksVO;
import com.jfeng.pan.server.modules.user.context.UserLoginContext;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.service.IUserService;
import com.jfeng.pan.server.modules.user.vo.UserInfoVO;
import lombok.AllArgsConstructor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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


    @Autowired
    private IFileService iFileService;

    @Autowired
    private IFileChunkService iFileChunkService;
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
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);
    }

    /**
     * 更新文件名称成功
     */
    @Test
    public void testUpdateFilenameSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();
        updateFilenameContext.setUserId(userId);
        updateFilenameContext.setFileId(fileId);
        updateFilenameContext.setNewFileName("new-folder-name");
        iUserFileService.updateFilename(updateFilenameContext);
    }
    /**
     * 更新文件名称失败--文件ID无效
     */
    @Test(expected = RPanBusinessException.class)
    public void testUpdateFilenameFailByWrongFileId(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();
        updateFilenameContext.setUserId(userId);
        updateFilenameContext.setFileId(fileId+1);
        updateFilenameContext.setNewFileName("new-folder-name");
        iUserFileService.updateFilename(updateFilenameContext);
    }

    /**
     * 更新文件名称失败--当前用户没有修改该文件名称的权限
     */
    @Test(expected = RPanBusinessException.class)
    public void testUpdateFilenameFailByWrongUserId(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();
        updateFilenameContext.setUserId(userId+1);
        updateFilenameContext.setFileId(fileId);
        updateFilenameContext.setNewFileName("new-folder-name");
        iUserFileService.updateFilename(updateFilenameContext);
    }

    /**
     * 更新文件名称失败--不能与当前文件夹名称一致
     */
    @Test(expected = RPanBusinessException.class)
    public void testUpdateFilenameFailByWrongFileName(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();
        updateFilenameContext.setUserId(userId);
        updateFilenameContext.setFileId(fileId);
        updateFilenameContext.setNewFileName("folder-name");
        iUserFileService.updateFilename(updateFilenameContext);
    }

    /**
     * 更新文件名称失败--当前文件名称已被使用
     */
    @Test(expected = RPanBusinessException.class)
    public void testUpdateFilenameFailByWrongUsedFileName(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name-1");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name-2");
        fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();
        updateFilenameContext.setUserId(userId);
        updateFilenameContext.setFileId(fileId);
        updateFilenameContext.setNewFileName("folder-name-2");
        iUserFileService.updateFilename(updateFilenameContext);
    }

    /**
     * 校验文件删除失败——非法的文件ID
     */
    @Test(expected = RPanBusinessException.class)
    public void tesDeleteFileFailByWrongFileId(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);


        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        DeleteFileContext deleteFileContext = new DeleteFileContext();
        List<Long> fileIdList = new ArrayList<>();
        fileIdList.add(fileId+1L);
        deleteFileContext.setFileIdList(fileIdList);
        deleteFileContext.setUserId(userId);

        iUserFileService.deleteFile(deleteFileContext);
    }

    /**
     * 校验文件删除失败——非法的用户ID
     */
    @Test(expected = RPanBusinessException.class)
    public void tesDeleteFileFailByWrongUserId(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);


        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        DeleteFileContext deleteFileContext = new DeleteFileContext();
        List<Long> fileIdList = new ArrayList<>();
        fileIdList.add(fileId);
        deleteFileContext.setFileIdList(fileIdList);
        deleteFileContext.setUserId(userId+1L);

        iUserFileService.deleteFile(deleteFileContext);
    }

    /**
     * 校验用户删除文件成功
     */
    @Test
    public void tesDeleteFileSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        DeleteFileContext deleteFileContext = new DeleteFileContext();
        List<Long> fileIdList = new ArrayList<>();
        fileIdList.add(fileId);
        deleteFileContext.setFileIdList(fileIdList);
        deleteFileContext.setUserId(userId);

        iUserFileService.deleteFile(deleteFileContext);
    }

    /**
     * 测试秒传功能成功
     */
    @Test
    public void testSecUploadSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        String identifier = "identifier";

        RPanFile record = new RPanFile();
        record.setFileId(IdUtil.get());
        record.setFilename("filename");
        record.setRealPath("realPath");
        record.setFileSize("fileSize");
        record.setFileSizeDesc("desc");
        record.setFileSuffix("suffix");
        record.setFilePreviewContentType("");
        record.setIdentifier(identifier);
        record.setCreateUser(userId);
        record.setCreateTime(new Date());

        boolean save = iFileService.save(record);
        Assert.isTrue(save);

        SecUploadContext context = new SecUploadContext();
        context.setFilename("filename");
        context.setIdentifier(identifier);
        context.setUserId(userId);
        context.setParentId(userInfoVO.getRootFiled());

        boolean success = iUserFileService.SecUpload(context);
        Assert.isTrue(success);
    }

    /**
     * 测试秒传功能失败
     */
    @Test
    public void testSecUploadFail(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        String identifier = "identifier";

        SecUploadContext context = new SecUploadContext();
        context.setFilename("filename");
        context.setIdentifier(identifier);
        context.setUserId(userId);
        context.setParentId(userInfoVO.getRootFiled());

        boolean success = iUserFileService.SecUpload(context);
        Assert.isFalse(success);
    }

    /**
     * 测试单文件上传成功
     */
    @Test
    public void testUploadSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        FileUploadContext context = new FileUploadContext();
        MultipartFile file = generateMultipartFile();
        context.setFile(file);
        context.setFilename(file.getName());
        context.setParentId(userInfoVO.getRootFiled());
        context.setUserId(userId);
        context.setIdentifier("12345678");
        context.setTotalSize(file.getSize());
        iUserFileService.upload(context);

        QueryFileListContext queryFileListContext = new QueryFileListContext();
        queryFileListContext.setDelFlag(DelFlagEnum.NO.getCode());
        queryFileListContext.setUserId(userId);
        queryFileListContext.setParentId(userInfoVO.getRootFiled());
        List<RPanUserFileVO> fileList = iUserFileService.getFileList(queryFileListContext);
        Assert.notEmpty(fileList);
        Assert.isTrue(fileList.size() == 1);
    }
    /**
     * 测试查询用户已上传分片信息成功
     */
    @Test
    public void  testQueryUploadedChunksSuccess(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        String identifier = "123456789";

        RPanFileChunk record = new RPanFileChunk();
        record.setId(IdUtil.get());
        record.setIdentifier(identifier);
        record.setRealPath("realPath");
        record.setChunkNumber(1);
        record.setExpirationTime(DateUtil.offsetDay(new Date(), 1));
        record.setCreateUser(userId);
        record.setCreateTime(new Date());
        boolean save = iFileChunkService.save(record);
        Assert.isTrue(save);

        QueryUploadedChunksContext context = new QueryUploadedChunksContext();
        context.setUserId(userId);
        context.setIdentifier(identifier);

        UploadedChunksVO uploadedChunksVO  = iUserFileService.getUploadedChunks(context);
        Assert.notNull(uploadedChunksVO);
        Assert.notEmpty(uploadedChunksVO.getUploadedChunks());
    }

    /**
     * 测试文件分片上传成功
     * 多文件分片并发上传
     */
    @Test
    public void uploadWithChunkTest() throws InterruptedException {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CountDownLatch countDownLatch = new CountDownLatch(10);
        for (int i = 0; i < 10 ; i++){
            new ChunkUpload(countDownLatch, i+1, 10, iUserFileService, userId, userInfoVO.getRootFiled()).start();
        }
        countDownLatch.await();
    }

    /**
     * 测试文件夹树查询
     */
    @Test
    public void getFolderTreeNodeVOListTest(){
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFiled());
        createFolderContext.setFolderName("folder-name-1");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        createFolderContext.setFolderName("folder-name-2");
        fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        createFolderContext.setFolderName("folder-name-2-1");
        createFolderContext.setParentId(fileId);
        fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        QueryFolderTreeContext context = new QueryFolderTreeContext();
        context.setUserId(userId);
        List<FolderTreeNodeVO> folderTree = iUserFileService.getFolderTree(context);

        Assert.isTrue(folderTree.size() == 1);
        folderTree.forEach(FolderTreeNodeVO::print);
    }

    /********************************* private ************************************/

    /**
     * 文件分片上传器
     */
    @AllArgsConstructor
    private static class ChunkUpload extends Thread{
        private CountDownLatch countDownLatch;
        private Integer chunk;
        private Integer chunks;
        private IUserFileService iUserFileService;
        private Long userId;
        private Long parentId;


        /**
         * 1、上传文件分片
         * 2、根据上传的结果调用文件分片合并
         */
        @Override
        public void run() {
            super.run();
            MultipartFile file = generateMultipartFile();
            Long totalSize = file.getSize() * chunks;
            String filename = "test.txt";
            String identifier = "123456789";

            FileChunkUploadContext context = new FileChunkUploadContext();
            context.setFilename(filename);
            context.setIdentifier(identifier);
            context.setChunkNumber(chunk);
            context.setTotalChunks(chunks);
            context.setCurrentChunkSize(file.getSize());
            context.setTotalSize(totalSize);
            context.setFile(file);
            context.setUserId(userId);

            FileChunkUploadVO vo = iUserFileService.chunkUpload(context);
            if(vo.getMergeFlag().equals(MergeFlagEnum.READY.getCode())){
                System.out.println("分片" + chunk + "检测到可以合并分片");

                FileChunkMergeContext mergeContext = new FileChunkMergeContext();
                mergeContext.setFilename(filename);
                mergeContext.setIdentifier(identifier);
                mergeContext.setTotalSize(totalSize);
                mergeContext.setParentId(parentId);
                mergeContext.setUserId(userId);

                iUserFileService.mergeFile(mergeContext);
                countDownLatch.countDown();
            }else{
                countDownLatch.countDown();
            }
        }
    }

    /**
     * 生成的网络文件实体
     * @return
     */
    private static MultipartFile generateMultipartFile() {
        MultipartFile file = null;
        try {
            file = new MockMultipartFile("file", "test.txt", "multipart/form-data", "test upload contedt".getBytes(StandardCharsets.UTF_8));
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
