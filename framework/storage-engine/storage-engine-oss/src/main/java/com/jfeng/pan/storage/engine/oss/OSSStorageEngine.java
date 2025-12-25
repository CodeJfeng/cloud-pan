package com.jfeng.pan.storage.engine.oss;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSONObject;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.*;
import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.FileUtil;
import com.jfeng.pan.core.utils.UUIDUtil;
import com.jfeng.pan.storage.engine.core.AbstractStorageEngine;
import com.jfeng.pan.storage.engine.core.context.*;
import com.jfeng.pan.storage.engine.oss.config.OssStorageEngineConfig;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class OSSStorageEngine extends AbstractStorageEngine {

    private static  final Integer TEN_THOUSAND_INT = 10000;

    private static final String CACHE_KEY_TEMPLATE = "oss_cache_upload_id_%s_%s";

    private static final String IDENTIFIER_KEY = "identifier";

    private static final String UPLOAD_ID_KEY = "uploadId";

    private static final String USER_ID_KEY = "userId";

    private static final String PART_NUMBER_KEY = "partNumber";

    private static final String E_TAG_KEY = "eTag";

    private static final String PART_SIZE_KEY = "partSizeKey";

    private static final String PART_CRC_KEY = "partCRC";

    @Autowired
    private OssStorageEngineConfig config;

    @Autowired
    private OSSClient client;

    /**
     * 单文件上传
     * 执行保存物理文件的动作
     *
     * @param context
     * @throws IOException
     */
    @Override
    protected void doStore(StoreFileContext context) throws IOException {
        String realPath = getFilePath(FileUtil.getFileSuffix(context.getFilename()));
        client.putObject(config.getBucketName(), realPath, context.getInputStream());
        context.setRealPath(realPath);
    }

    /**
     * 执行删除物理文件的动作
     *
     * @param context
     */
    @Override
    protected void doDelete(DeleteFileContext context) throws IOException {

    }

    /**
     * 文件分片上传处理
     *
     * <p>实现OSS分片上传的三个核心步骤：</p>
     * <ol>
     *   <li><b>初始化</b>：获取全局唯一的uploadId</li>
     *   <li><b>并发上传</b>：多线程上传文件分片，每个分片携带uploadId</li>
     *   <li><b>合并分片</b>：所有分片上传完成后触发合并操作</li>
     * </ol>
     *
     * <p><b>技术挑战与解决方案：</b></p>
     * <ul>
     *   <li><b>并发控制</b>：加锁，我们目前首先按单体架构去考虑，使用JVM的锁去保证一个线程初始化文件分片上传，如果后续扩展分布式的架构，需要更换分布式锁</li>
     *   <li><b>状态共享</b>：使用缓存，缓存分为本地缓存以及分布式缓存（比如 redis)，优于我们当前是一个单体架构，可以考虑使用本地缓存，但是，后期的项目升级为分布式架构
     *    升级之后，同样要升级我们的缓存为分布式缓存， 与其后期升级，我们第一版就支持分布缓存比较好</li>
     *   <li><b>参数传递</b>：想把每一个文件的Kye都能够通过文件url来获取，就需要定义一种数据格式，支持我们添加附件数据，并可以很方便的解析出来，
     *   采用URL格式(fileRealPath?paramKey=paramValue)封装上传参数，
     *       支持独立解析每个分片信息</li>
     * </ul>
     *
     * <p><b>执行流程：</b></p>
     * <ol>
     *   <li>校验文件分片数量（≤10000）</li>
     *   <li>获取缓存键，尝试从缓存读取uploadId和objectName</li>
     *   <li>若缓存不存在，执行初始化获取uploadId并缓存</li>
     *   <li>并发执行分片上传</li>
     *   <li>封装上传参数为可解析的URL格式，存入上下文供业务层使用</li>
     * </ol>
     *
     * @param context 上传上下文，包含文件信息及配置参数
     * @throws IOException 文件读写异常或OSS通信异常
     * @throws IllegalArgumentException 分片数量超过限制或参数无效
     */
    @Override
    protected synchronized void doStoreChunk(StoreFileChunkContext context) throws IOException {
        if(context.getTotalChunks() > TEN_THOUSAND_INT){
            throw new RPanBusinessException("分片数超过了限制，分片数不得大于：" + TEN_THOUSAND_INT);
        }
        String cacheKey = getCacheKey(context.getIdentifier(), context.getUserId());
        ChunkUploadEntity entity = getCache().get(cacheKey, ChunkUploadEntity.class);
        if (Objects.isNull(entity)){
            entity = initChunkUpload(context.getFilename(), cacheKey);
        }
        UploadPartRequest request = new UploadPartRequest();
        request.setBucketName(config.getBucketName());
        request.setKey(entity.getObjectKey());
        request.setUploadId(entity.getUploadId());
        request.setInputStream(context.getInputStream());
        request.setPartSize(context.getCurrentChunkSize());
        request.setPartNumber(context.getChunkNumber());

        UploadPartResult result = client.uploadPart(request);
        if(Objects.isNull(result)){
            throw new RPanBusinessException("文件分片上传失败");
        }

        PartETag partETag = result.getPartETag();

        // 拼装文件分片的url
        JSONObject params = new JSONObject();
        params.put(IDENTIFIER_KEY, context.getIdentifier());
        params.put(UPLOAD_ID_KEY, entity.getUploadId());
        params.put(USER_ID_KEY, context.getUserId());
        params.put(PART_NUMBER_KEY, context.getChunkNumber());
        params.put(E_TAG_KEY, partETag.getETag());
        params.put(PART_SIZE_KEY, partETag.getPartSize());
        params.put(PART_CRC_KEY, partETag.getPartCRC());

        String realPath = assembleUrl(entity.getObjectKey(), params);
        context.setRealPath(realPath);
    }

    /**
     * 执行文件分片的动作
     * @param context
     */
    @Override
    protected void doMergeFile(MergeFileContext context) throws IOException {

    }

    /**
     * 读取文件内容并写入到输出流中
     *
     * @param context
     */
    @Override
    protected void doReadFile(ReadFileContext context) throws IOException {

    }

    /********************************************* private ************************************************************/

    /**
     * 该实体为文件分片上传初始化之后的全局信息载体
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    private static class ChunkUploadEntity implements Serializable {
        @Serial
        private static final long serialVersionUID = -32443597655643L;

        /**
         * 分片上传全局唯一的uploadId
         */
        private String uploadId;

        /**
         * 文件分片上传的实体名称
         */
        private String objectKey;
    }

    /**
     * 获取分片上传的缓存key
     *
     * @param identifier
     * @param userId
     * @return
     */
    private String getCacheKey(String identifier, Long userId) {
        return String.format(CACHE_KEY_TEMPLATE, identifier, userId);
    }


    /**
     * 获取对象的完整名称
     * /年/月/日/UUID.fileSuffix
     *
     * @param fileSuffix
     * @return
     */
    private String getFilePath(String fileSuffix) {
        return DateUtil.thisYear()
                + RPanConstants.SLASH_STR
                + DateUtil.thisMonth()
                + RPanConstants.SLASH_STR
                + DateUtil.thisDayOfMonth()
                + RPanConstants.SLASH_STR
                + UUIDUtil.getUUID()
                + fileSuffix;
    }


    /**
     * 拼装Url
     *
     * @param baseUrl
     * @param params
     * @return baseUrl?paramKey1=paramValue&paramKey2=paramValue
     */
    private String assembleUrl(String baseUrl, JSONObject params) {
        if (Objects.isNull(params) || params.isEmpty()) {
            return baseUrl;
        }
        StringBuilder urlStringBuffer = new StringBuilder(baseUrl);
        urlStringBuffer.append(RPanConstants.QUESTION_MARK_STR);
        List<String> paramsList = Lists.newArrayList();
        StringBuilder urlParamsStringBuffer = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            urlParamsStringBuffer.setLength(RPanConstants.ZERO_INT);
            urlParamsStringBuffer.append(key);
            urlParamsStringBuffer.append(RPanConstants.EQUALS_MARK_STR);
            urlParamsStringBuffer.append(value);
            paramsList.add(urlParamsStringBuffer.toString());
        }
        return urlStringBuffer.append(String.join(RPanConstants.AND_MARK_STR, paramsList)).toString();
    }

    /**
     * 获取基础URL
     *
     * @param url
     * @return
     */
    private String getBaseUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return RPanConstants.EMPTY_STR;
        }
        if (checkHaveParams(url)) {
            return url.split(getSplitMark(RPanConstants.QUESTION_MARK_STR))[0];
        }
        return url;
    }

    /**
     * 获取截取字符串的关键标识
     * 由于java的字符串分割会按照正则去截取
     * 我们的URL会影响标识的识别，故添加左右中括号去分组
     *
     * @param mark
     * @return
     */
    private String getSplitMark(String mark) {
        return RPanConstants.LEFT_BRACKET_STR +
                mark +
                RPanConstants.RIGHT_BRACKET_STR;
    }

    /**
     * 分析URL参数
     *
     * @param url
     * @return
     */
    private com.alibaba.fastjson.JSONObject analysisUrlParams(String url) {
        com.alibaba.fastjson.JSONObject result = new com.alibaba.fastjson.JSONObject();
        if (!checkHaveParams(url)) {
            return result;
        }
        String paramsPart = url.split(getSplitMark(RPanConstants.QUESTION_MARK_STR))[1];
        if (StringUtils.isNotBlank(paramsPart)) {
            List<String> paramPairList = Arrays.asList(paramsPart.split(RPanConstants.AND_MARK_STR));
            paramPairList.forEach(paramPair -> {
                String[] paramArr = paramPair.split(getSplitMark(RPanConstants.EQUALS_MARK_STR));
                if (paramArr != null && paramArr.length == RPanConstants.TWO_INT) {
                    result.put(paramArr[0], paramArr[1]);
                }
            });
        }
        return result;
    }

    /**
     * 检查是否是含有参数的URL
     *
     * @param url
     * @return
     */
    private boolean checkHaveParams(String url) {
        return StringUtils.isNotBlank(url) && url.contains(RPanConstants.QUESTION_MARK_STR);
    }

    /**
     * 初始化文件分片上传
     * 1、执行初始化的请求
     * 2、保存初始化结果到缓存
     *
     * @param filename
     * @param cacheKey
     * @return
     */
    private ChunkUploadEntity initChunkUpload(String filename, String cacheKey) {
        String filePath = getFilePath(filename);
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(config.getBucketName(), filePath);
        InitiateMultipartUploadResult result = client.initiateMultipartUpload(request);
        if(Objects.isNull(result)){
            throw new RPanBusinessException("文件分片上传初始化失败");
        }
        ChunkUploadEntity entity = new ChunkUploadEntity();
        entity.setObjectKey(filename);
        entity.setUploadId(result.getUploadId());
        getCache().put(cacheKey, entity);
        return entity;
    }



}
