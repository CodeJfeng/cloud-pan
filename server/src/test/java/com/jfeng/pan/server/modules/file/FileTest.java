package com.jfeng.pan.server.modules.file;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
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
import com.jfeng.pan.server.modules.file.vo.*;
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
import org.springframework.test.annotation.Rollback;
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
@Rollback
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
    public void testQueryUserFileListSuccess() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        QueryFileListContext context = new QueryFileListContext();
        context.setUserId(userId);
        context.setParentId(userInfoVO.getRootFileId());
        context.setDelFlag(DelFlagEnum.NO.getCode());
        context.setFileTypeArray(null);
        List<RPanUserFileVO> result = iUserFileService.getFileList(context);
        Assert.isTrue(CollectionUtil.isEmpty(result));
    }

    /**
     * 测试创建文件夹成功
     */
    @Test
    public void testCreateFolderSuccess() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);
    }

    /**
     * 更新文件名称成功
     */
    @Test
    public void testUpdateFilenameSuccess() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();
        updateFilenameContext.setUserId(userId);
        updateFilenameContext.setFileId(fileId);
        updateFilenameContext.setNewFilename("new-folder-name");
        iUserFileService.updateFilename(updateFilenameContext);
    }

    /**
     * 更新文件名称失败--文件ID无效
     */
    @Test(expected = RPanBusinessException.class)
    public void testUpdateFilenameFailByWrongFileId() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();
        updateFilenameContext.setUserId(userId);
        updateFilenameContext.setFileId(fileId + 1);
        updateFilenameContext.setNewFilename("new-folder-name");
        iUserFileService.updateFilename(updateFilenameContext);
    }

    /**
     * 更新文件名称失败--当前用户没有修改该文件名称的权限
     */
    @Test(expected = RPanBusinessException.class)
    public void testUpdateFilenameFailByWrongUserId() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();
        updateFilenameContext.setUserId(userId + 1);
        updateFilenameContext.setFileId(fileId);
        updateFilenameContext.setNewFilename("new-folder-name");
        iUserFileService.updateFilename(updateFilenameContext);
    }

    /**
     * 更新文件名称失败--不能与当前文件夹名称一致
     */
    @Test(expected = RPanBusinessException.class)
    public void testUpdateFilenameFailByWrongFileName() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();
        updateFilenameContext.setUserId(userId);
        updateFilenameContext.setFileId(fileId);
        updateFilenameContext.setNewFilename("folder-name");
        iUserFileService.updateFilename(updateFilenameContext);
    }

    /**
     * 更新文件名称失败--当前文件名称已被使用
     */
    @Test(expected = RPanBusinessException.class)
    public void testUpdateFilenameFailByWrongUsedFileName() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name-1");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name-2");
        fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        UpdateFilenameContext updateFilenameContext = new UpdateFilenameContext();
        updateFilenameContext.setUserId(userId);
        updateFilenameContext.setFileId(fileId);
        updateFilenameContext.setNewFilename("folder-name-2");
        iUserFileService.updateFilename(updateFilenameContext);
    }

    /**
     * 校验文件删除失败——非法的文件ID
     */
    @Test(expected = RPanBusinessException.class)
    public void tesDeleteFileFailByWrongFileId() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        DeleteFileContext deleteFileContext = new DeleteFileContext();
        List<Long> fileIdList = new ArrayList<>();
        fileIdList.add(fileId + 1L);
        deleteFileContext.setFileIdList(fileIdList);
        deleteFileContext.setUserId(userId);

        iUserFileService.deleteFile(deleteFileContext);
    }

    /**
     * 校验文件删除失败——非法的用户ID
     */
    @Test(expected = RPanBusinessException.class)
    public void tesDeleteFileFailByWrongUserId() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name");
        Long fileId = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(fileId);

        DeleteFileContext deleteFileContext = new DeleteFileContext();
        List<Long> fileIdList = new ArrayList<>();
        fileIdList.add(fileId);
        deleteFileContext.setFileIdList(fileIdList);
        deleteFileContext.setUserId(userId + 1L);

        iUserFileService.deleteFile(deleteFileContext);
    }

    /**
     * 校验用户删除文件成功
     */
    @Test
    public void tesDeleteFileSuccess() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
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
    public void testSecUploadSuccess() {
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
        context.setParentId(userInfoVO.getRootFileId());

        boolean success = iUserFileService.SecUpload(context);
        Assert.isTrue(success);
    }

    /**
     * 测试秒传功能失败
     */
    @Test
    public void testSecUploadFail() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        String identifier = "identifier";

        SecUploadContext context = new SecUploadContext();
        context.setFilename("filename");
        context.setIdentifier(identifier);
        context.setUserId(userId);
        context.setParentId(userInfoVO.getRootFileId());

        boolean success = iUserFileService.SecUpload(context);
        Assert.isFalse(success);
    }

    /**
     * 测试单文件上传成功
     */
    @Test
    public void testUploadSuccess() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        FileUploadContext context = new FileUploadContext();
        MultipartFile file = generateMultipartFile();
        context.setFile(file);
        context.setFilename(file.getName());
        context.setParentId(userInfoVO.getRootFileId());
        context.setUserId(userId);
        context.setIdentifier("12345678");
        context.setTotalSize(file.getSize());
        iUserFileService.upload(context);

        QueryFileListContext queryFileListContext = new QueryFileListContext();
        queryFileListContext.setDelFlag(DelFlagEnum.NO.getCode());
        queryFileListContext.setUserId(userId);
        queryFileListContext.setParentId(userInfoVO.getRootFileId());
        List<RPanUserFileVO> fileList = iUserFileService.getFileList(queryFileListContext);
        Assert.notEmpty(fileList);
        Assert.isTrue(fileList.size() == 1);
    }

    /**
     * 测试查询用户已上传分片信息成功
     */
    @Test
    public void testQueryUploadedChunksSuccess() {
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

        UploadedChunksVO uploadedChunksVO = iUserFileService.getUploadedChunks(context);
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
        for (int i = 0; i < 10; i++) {
            new ChunkUpload(countDownLatch, i + 1, 10, iUserFileService, userId, userInfoVO.getRootFileId()).start();
        }
        countDownLatch.await();
    }

    /**
     * 测试文件夹树查询
     */
    @Test
    public void getFolderTreeNodeVOListTest() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
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

    /**
     * 测试文件转移成功
     */
    @Test
    public void testTransferFileSuccess() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name-1");

        Long folder1 = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder1);

        createFolderContext.setFolderName("folder-name-2");
        Long folder2 = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder2);

        TransferFileConext transferFileContext = new TransferFileConext();
        transferFileContext.setTargetParentId(folder1);
        transferFileContext.setFileIdList(Lists.newArrayList(folder2));
        transferFileContext.setUserId(userId);

        iUserFileService.transfer(transferFileContext);

        QueryFileListContext queryFileListContext = new QueryFileListContext();
        queryFileListContext.setUserId(userId);
        queryFileListContext.setDelFlag(DelFlagEnum.NO.getCode());
        queryFileListContext.setParentId(userInfoVO.getRootFileId());
        List<RPanUserFileVO> records = iUserFileService.getFileList(queryFileListContext);
        Assert.notNull(records);
    }

    /**
     * 测试文件转移失败——目标文件夹是要转移的文件列表中的文件夹或者其子文件夹
     */
    @Test(expected = RPanBusinessException.class)
    public void testTransferFileFail() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name-1");

        Long folder1 = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder1);

        createFolderContext.setParentId(folder1);
        createFolderContext.setFolderName("folder-name-2");
        Long folder2 = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder2);

        TransferFileConext transferFileContext = new TransferFileConext();
        transferFileContext.setTargetParentId(folder2);
        transferFileContext.setFileIdList(Lists.newArrayList(folder1));
        transferFileContext.setUserId(userId);

        iUserFileService.transfer(transferFileContext);

        QueryFileListContext queryFileListContext = new QueryFileListContext();
        queryFileListContext.setUserId(userId);
        queryFileListContext.setDelFlag(DelFlagEnum.NO.getCode());
        queryFileListContext.setParentId(userInfoVO.getRootFileId());
        List<RPanUserFileVO> records = iUserFileService.getFileList(queryFileListContext);
        Assert.notNull(records);
    }

    /**
     * 测试文件复制成功
     */
    @Test
    public void testCopyFileSuccess() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name-1");

        Long folder1 = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder1);

        createFolderContext.setParentId(folder1);
        createFolderContext.setFolderName("folder-name-2");
        Long folder2 = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder2);

        CopyFileContext copyFileContext = new CopyFileContext();
        copyFileContext.setTargetParentId(folder1);
        copyFileContext.setFileIdList(Lists.newArrayList(folder2));
        copyFileContext.setUserId(userId);
        iUserFileService.copy(copyFileContext);

        QueryFileListContext queryFileListContext = new QueryFileListContext();
        queryFileListContext.setUserId(userId);
        queryFileListContext.setDelFlag(DelFlagEnum.NO.getCode());
        queryFileListContext.setParentId(folder1);
        List<RPanUserFileVO> records = iUserFileService.getFileList(queryFileListContext);
        Assert.notNull(records);
    }

    /**
     * 测试文件复制失败——目标文件夹是要转移的文件列表中的文件夹或者其子文件夹
     */
    @Test(expected = RPanBusinessException.class)
    public void testCopyFileFail() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name-1");

        Long folder1 = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder1);

        createFolderContext.setParentId(folder1);
        createFolderContext.setFolderName("folder-name-2");
        Long folder2 = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder2);

        CopyFileContext copyFileContext = new CopyFileContext();
        copyFileContext.setTargetParentId(folder2);
        copyFileContext.setFileIdList(Lists.newArrayList(folder1));
        copyFileContext.setUserId(userId);
        iUserFileService.copy(copyFileContext);

        QueryFileListContext queryFileListContext = new QueryFileListContext();
        queryFileListContext.setUserId(userId);
        queryFileListContext.setDelFlag(DelFlagEnum.NO.getCode());
        queryFileListContext.setParentId(folder1);
        List<RPanUserFileVO> records = iUserFileService.getFileList(queryFileListContext);
        Assert.notNull(records);
    }

    /**
     * 测试文件搜索成功
     */
    @Test
    public void testSearchSuccess() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name-1");

        Long folder1 = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder1);

        FileSearchContext searchContext = new FileSearchContext();
        searchContext.setKeyword("folder-name");
        searchContext.setUserId(userId);
        List<FileSearchResultVO> result = iUserFileService.search(searchContext);
        Assert.notEmpty(result);

        searchContext.setKeyword("name-1");
        result = iUserFileService.search(searchContext);
        Assert.isTrue(CollectionUtil.isEmpty(result));

    }

    /**
     * 测试查询文件面包屑导航列表成功过
     */
    @Test
    public void testGetBreadcrumbsSuccess() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setUserId(userId);
        createFolderContext.setParentId(userInfoVO.getRootFileId());
        createFolderContext.setFolderName("folder-name-1");

        Long folder1 = iUserFileService.createFolder(createFolderContext);
        Assert.notNull(folder1);

        QueryBreadcrumbContext queryBreadcrumbContext = new QueryBreadcrumbContext();
        queryBreadcrumbContext.setFileId(folder1);
        queryBreadcrumbContext.setUserId(userId);

        List<BreadcrumbVO> result = iUserFileService.getBreadcrumbs(queryBreadcrumbContext);
        Assert.notEmpty(result);
        Assert.isTrue(result.size() == 2);
    }

    // 验证预签名URL直传测试成功
    /**
     * 测试单文件预签名URL生成
     */
    @Test
    public void testGeneratePresignedUrl() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        GeneratePresignedUrlContext context = new GeneratePresignedUrlContext();
        context.setFilename("test-file.txt");
        context.setTotalSize(1024L);
        context.setContentType("text/plain");
        context.setUserId(userId);

        PresignedUrlVO vo = iUserFileService.generatePresignedUrl(context);

        Assert.notNull(vo);
        Assert.notNull(vo.getUploadUrl());
        Assert.notNull(vo.getObjectKey());
        System.out.println("预签名URL: " + vo.getUploadUrl());
        System.out.println("ObjectKey: " + vo.getObjectKey());
    }

    /**
     * 测试单文件直传完整流程
     * 1. 生成预签名URL
     * 2. 客户端使用PUT请求直传S3
     * 3. 回调完成直传接口
     */
    @Test
    public void testDirectUploadCompleteFlow() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        String filename = "test-direct-upload.txt";
        String fileContent = "Hello, this is a test file for direct upload!";
        byte[] fileBytes = fileContent.getBytes(StandardCharsets.UTF_8);

        GeneratePresignedUrlContext context = new GeneratePresignedUrlContext();
        context.setFilename(filename);
        context.setTotalSize((long) fileBytes.length);
        context.setContentType("text/plain");
        context.setUserId(userId);

        PresignedUrlVO vo = iUserFileService.generatePresignedUrl(context);
        Assert.notNull(vo);
        Assert.notNull(vo.getUploadUrl());
        Assert.notNull(vo.getObjectKey());

        String uploadUrl = vo.getUploadUrl();
        String objectKey = vo.getObjectKey();
        String identifier = IdUtil.get().toString();

        // url直传
        HttpResponse response = HttpRequest.put(uploadUrl)
                .header("Content-Type", "text/plain")
                .body(fileBytes)
                .execute();

        Assert.isTrue(response.isOk());

        CompleteDirectUploadContext completeContext = new CompleteDirectUploadContext();
        completeContext.setObjectKey(objectKey);
        completeContext.setFilename(filename);
        completeContext.setTotalSize((long) fileBytes.length);
        completeContext.setIdentifier(identifier);
        completeContext.setParentId(userInfoVO.getRootFileId());
        completeContext.setUserId(userId);

        iUserFileService.completeDirectUpload(completeContext);

        RPanFile fileRecord = iFileService.lambdaQuery()
                .eq(RPanFile::getIdentifier, identifier)
                .one();
        Assert.notNull(fileRecord);
        Assert.isTrue(filename.equals(fileRecord.getFilename()));
        Assert.isTrue(fileBytes.length == Long.parseLong(fileRecord.getFileSize()));

        System.out.println("直传测试完成，文件记录ID: " + fileRecord.getFileId());
    }

    /**
     * 测试分片上传初始化
     */
    @Test
    public void testInitMultipartUpload() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        GeneratePresignedMultipartUrlContext context = new GeneratePresignedMultipartUrlContext();
        context.setFilename("large-file.bin");
        context.setTotalSize(10 * 1024 * 1024L);
        context.setTotalChunks(2);
        context.setContentType("application/octet-stream");
        context.setUserId(userId);

        PresignedUrlVO vo = iUserFileService.initMultipartUpload(context);

        Assert.notNull(vo);
        Assert.notNull(vo.getUploadUrl());
        Assert.notNull(vo.getObjectKey());
        Assert.notNull(vo.getUploadId());
        Assert.notNull(vo.getCacheKey());

        System.out.println("初始化URL: " + vo.getUploadUrl());
        System.out.println("ObjectKey: " + vo.getObjectKey());
        System.out.println("UploadId: " + vo.getUploadId());
        System.out.println("CacheKey: " + vo.getCacheKey());
    }

    /**
     * 测试分片上传完整流程
     * 1. 初始化分片上传
     * 2. 上传每个分片
     * 3. 完成分片上传
     */
    @Test
    public void testMultipartUploadCompleteFlow() {
        Long userId = register();
        UserInfoVO userInfoVO = info(userId);

        String filename = "multipart-test.bin";
        int totalChunks = 3;
        long chunkSize = 5 * 1024 * 1024L;
        long totalSize = chunkSize * totalChunks;
        // 预签名
        GeneratePresignedMultipartUrlContext initContext = new GeneratePresignedMultipartUrlContext();
        initContext.setFilename(filename);
        initContext.setTotalSize(totalSize);
        initContext.setTotalChunks(totalChunks);
        initContext.setContentType("application/octet-stream");
        initContext.setUserId(userId);
        PresignedUrlVO initVO = iUserFileService.initMultipartUpload(initContext);
        Assert.notNull(initVO);
        Assert.notNull(initVO.getUploadId());

        // 上传单文件
        String objectKey = initVO.getObjectKey();
        String uploadId = initVO.getUploadId();
        String identifier = IdUtil.get().toString();

        List<CompleteDirectUploadContext.PartInfo> parts = new ArrayList<>();

        for (int i = 1; i <= totalChunks; i++) {
            // 上传单文件
            GeneratePresignedPartUrlContext partContext = new GeneratePresignedPartUrlContext();
            partContext.setObjectKey(objectKey);
            partContext.setUploadId(uploadId);
            partContext.setPartNumber(i);
            partContext.setPartSize(chunkSize);
            partContext.setUserId(userId);
            // 每个文件生成一个url地址
            PresignedUrlVO partVO = iUserFileService.generatePresignedPartUrl(partContext);
            Assert.notNull(partVO);
            Assert.notNull(partVO.getUploadUrl());
            byte[] chunkData = new byte[(int) chunkSize];
            for (int j = 0; j < chunkSize; j++) {
                chunkData[j] = (byte) (i % 256);
            }
            // 客户端直传
            HttpResponse response = HttpRequest.put(partVO.getUploadUrl())
                    .header("Content-Type", "application/octet-stream")
                    .body(chunkData)
                    .execute();

            Assert.isTrue(response.isOk());

            String eTag = response.header("ETag");
            Assert.notBlank(eTag);

            CompleteDirectUploadContext.PartInfo partInfo = new CompleteDirectUploadContext.PartInfo();
            partInfo.setPartNumber(i);
            partInfo.setETag(eTag);
            parts.add(partInfo);

            System.out.println("分片 " + i + " 上传成功，ETag: " + eTag);
        }
        // 执行合并
        CompleteDirectUploadContext completeContext = new CompleteDirectUploadContext();
        completeContext.setObjectKey(objectKey);
        completeContext.setUploadId(uploadId);
        completeContext.setFilename(filename);
        completeContext.setTotalSize(totalSize);
        completeContext.setIdentifier(identifier);
        completeContext.setParentId(userInfoVO.getRootFileId());
        completeContext.setUserId(userId);
        completeContext.setParts(parts);

        iUserFileService.completeDirectUpload(completeContext);

        RPanFile fileRecord = iFileService.lambdaQuery()
                .eq(RPanFile::getIdentifier, identifier)
                .one();
        Assert.notNull(fileRecord);
        Assert.isTrue(filename.equals(fileRecord.getFilename()));

        System.out.println("分片上传测试完成，文件记录ID: " + fileRecord.getFileId());
    }

    /*************************************************************
     * private
     ****************************************************************/

    /**
     * 文件分片上传器
     */
    @AllArgsConstructor
    private static class ChunkUpload extends Thread {
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
            if (vo.getMergeFlag().equals(MergeFlagEnum.READY.getCode())) {
                System.out.println("分片" + chunk + "检测到可以合并分片");

                FileChunkMergeContext mergeContext = new FileChunkMergeContext();
                mergeContext.setFilename(filename);
                mergeContext.setIdentifier(identifier);
                mergeContext.setTotalSize(totalSize);
                mergeContext.setParentId(parentId);
                mergeContext.setUserId(userId);

                iUserFileService.mergeFile(mergeContext);
                countDownLatch.countDown();
            } else {
                countDownLatch.countDown();
            }
        }
    }

    /**
     * 生成的网络文件实体
     * 
     * @return
     */
    private static MultipartFile generateMultipartFile() {
        MultipartFile file = null;
        try {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < 1024 * 1024 * 5; i++) {
                sb.append("A");
            }
            file = new MockMultipartFile("file", "test.txt", "multipart/form-data",
                    sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    /**
     * 用户注册方法
     * 
     * @return 新用户Id
     */
    private Long register() {
        UserRegisterContext context = createRegisterContext();
        Long register = iUserService.register(context);
        Assert.isTrue(register > 0L);
        return register;
    }

    /**
     * 查询登录用户的基本信息
     * 
     * @return 返回用户的相应信息
     */
    private UserInfoVO info(Long userId) {
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
     * 
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
     * 
     * @return
     */
    private UserLoginContext createLoginContext() {
        UserLoginContext userLoginContext = new UserLoginContext();
        userLoginContext.setUsername(USERNAME);
        userLoginContext.setPassword(PASSWORD);
        return userLoginContext;
    }
}
