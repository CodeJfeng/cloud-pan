package com.jfeng.pan.storage.engine.rustfs;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSONObject;
import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.utils.FileUtil;
import com.jfeng.pan.core.utils.UUIDUtil;
import com.jfeng.pan.lock.core.annotation.Lock;
import com.jfeng.pan.storage.engine.core.AbstractStorageEngine;
import com.jfeng.pan.storage.engine.core.context.*;
import com.jfeng.pan.storage.engine.rustfs.config.RustfsStorageEngineConfig;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedCreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.presigner.model.CreateMultipartUploadPresignRequest;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RustFSStorageEngine extends AbstractStorageEngine {

    private static final Integer TEN_THOUSAND_INT = 10000;

    private static final String CACHE_KEY_TEMPLATE = "rustfs_cache_upload_id_%s_%s";

    private static final String IDENTIFIER_KEY = "identifier";

    private static final String UPLOAD_ID_KEY = "uploadId";

    private static final String USER_ID_KEY = "userId";

    private static final String PART_NUMBER_KEY = "partNumber";

    private static final String E_TAG_KEY = "eTag";

    private static final String PART_SIZE_KEY = "partSizeKey";

    @Autowired
    private RustfsStorageEngineConfig config;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DefaultRedisScript<String> checkAndPutRedisScript;

    @Autowired
    private DefaultRedisScript<String> getIfExistsRedisScript;

    /**
     * 存储物理文件到 RustFS（S3 兼容存储）
     * 1、生成文件存储路径（按日期+UUID）
     * 2、调用 S3 putObject API 上传文件
     *
     * @param context 文件存储上下文，包含文件名、文件大小、文件输入流等信息
     * @throws IOException 文件读写异常或 S3 通信异常
     */
    @Override
    protected void doStore(StoreFileContext context) throws IOException {
        String realPath = getFilePath(FileUtil.getFileSuffix(context.getFilename()));
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(config.getBucketName())
                        .key(realPath)
                        .build(),
                RequestBody.fromInputStream(context.getInputStream(), context.getTotalSize()));
        context.setRealPath(realPath);
    }

    /**
     * 删除物理文件
     * 1、解析文件路径，判断是否包含分片上传参数
     * 2、若包含分片参数，清理缓存并中止分片上传
     * 3、若不包含分片参数，直接删除 S3 对象
     *
     * @param context 文件删除上下文，包含要删除的文件路径列表
     * @throws IOException 文件删除异常或 S3 通信异常
     */
    @Override
    protected void doDelete(DeleteFileContext context) throws IOException {
        List<String> realFilePathList = context.getRealPathList();
        realFilePathList.forEach(realPath -> {
            if (checkHaveParams(realPath)) {
                JSONObject params = JSONObject.from(analysisUrlParams(realPath));
                if (Objects.nonNull(params) && !params.isEmpty()) {
                    String upload = params.getString(UPLOAD_ID_KEY);
                    String identifier = params.getString(IDENTIFIER_KEY);
                    Long userId = params.getLongValue(USER_ID_KEY);
                    String cacheKey = getCacheKey(identifier, userId);

                    getCache().evict(cacheKey);

                    try {
                        s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                                .bucket(config.getBucketName())
                                .key(getBaseUrl(realPath))
                                .uploadId(upload)
                                .build());
                    } catch (Exception ignored) {
                    }
                }
            } else {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(config.getBucketName())
                        .key(realPath)
                        .build());
            }
        });
    }

    /**
     * 文件分片上传处理
     *
     * <p>
     * 实现RustFS分片上传的三个核心步骤：
     * </p>
     * <ol>
     * <li><b>初始化</b>：获取全局唯一的uploadId（需要分布式锁保护）</li>
     * <li><b>并发上传</b>：多线程上传文件分片，每个分片携带uploadId（无需加锁）</li>
     * <li><b>合并分片</b>：所有分片上传完成后触发合并操作</li>
     * </ol>
     *
     * <p>
     * <b>技术挑战与解决方案：</b>
     * </p>
     * <ul>
     * <li><b>并发控制</b>：只在初始化uploadId时加锁，分片上传不加锁，支持真正的并发上传</li>
     * <li><b>状态共享</b>：使用Redis分布式缓存存储uploadId，支持多节点部署</li>
     * <li><b>参数传递</b>：采用URL格式(fileRealPath?paramKey=paramValue)封装上传参数，支持独立解析每个分片信息</li>
     * </ul>
     *
     * <p>
     * <b>执行流程：</b>
     * </p>
     * <ol>
     * <li>校验文件分片数量（≤10000）</li>
     * <li>校验分片大小是否符合最小要求</li>
     * <li>获取缓存键，尝试从缓存读取uploadId和objectName</li>
     * <li>若缓存不存在，使用双重检查锁定模式执行初始化</li>
     * <li>并发执行分片上传（无需加锁）</li>
     * <li>封装上传参数为可解析的URL格式，存入上下文供业务层使用</li>
     * </ol>
     *
     * <p>
     * <b>改进点（对比旧版本）：</b>
     * </p>
     * <ul>
     * <li>旧版本：整个doStoreChunk方法加锁，导致分片串行上传，性能差</li>
     * <li>新版本：只在初始化uploadId时加锁，分片上传完全并发，性能提升N倍（N=分片数）</li>
     * <li>旧版本：锁过期时间10秒，大文件初始化可能超时</li>
     * <li>新版本：锁过期时间60秒，配合双重检查确保安全性</li>
     * </ul>
     *
     * @param context 上传上下文，包含文件信息及配置参数
     * @throws IOException              文件读写异常或RustFS通信异常
     * @throws IllegalArgumentException 分片数量超过限制或参数无效
     */
    @Override
    protected void doStoreChunk(StoreFileChunkContext context) throws IOException {
        if (context.getTotalChunks() > TEN_THOUSAND_INT) {
            throw new RPanBusinessException("分片数超过了限制，分片数不得大于：" + TEN_THOUSAND_INT);
        }

        long minPartSize = config.getMinPartSize();
        long currentChunkSize = context.getCurrentChunkSize();

        if (currentChunkSize < minPartSize && context.getChunkNumber() < context.getTotalChunks()) {
            currentChunkSize = minPartSize;
        }

        String cacheKey = getCacheKey(context.getIdentifier(), context.getUserId());

        ChunkUploadEntity entity = getOrInitChunkUploadEntity(context.getFilename(), cacheKey);

        UploadPartResponse uploadPartResponse = s3Client.uploadPart(UploadPartRequest.builder()
                .bucket(config.getBucketName())
                .key(entity.getObjectKey())
                .uploadId(entity.getUploadId())
                .partNumber(context.getChunkNumber())
                .contentLength(currentChunkSize)
                .build(),
                RequestBody.fromInputStream(context.getInputStream(), currentChunkSize));

        String eTag = uploadPartResponse.eTag();

        JSONObject params = new JSONObject();
        params.put(IDENTIFIER_KEY, context.getIdentifier());
        params.put(UPLOAD_ID_KEY, entity.getUploadId());
        params.put(USER_ID_KEY, context.getUserId());
        params.put(PART_NUMBER_KEY, context.getChunkNumber());
        params.put(E_TAG_KEY, eTag);

        String realPath = assembleUrl(entity.getObjectKey(), params);
        context.setRealPath(realPath);
    }

    /**
     * 合并文件分片
     * 使用分布式锁保护，防止并发合并冲突
     * 1、从缓存获取 uploadId 和 objectKey
     * 2、从分片路径列表中解析每个分片的 partNumber 和 eTag
     * 3、调用 S3 completeMultipartUpload API 合并分片
     * 4、清理缓存
     *
     * @param context 文件分片合并上下文，包含文件名、文件唯一标识、分片路径列表等信息
     * @throws IOException 文件合并异常或 S3 通信异常
     */
    @Lock(name = "RustfsMergeFileLock", keys = { "#context.identifier", "#context.userId" }, expireSecond = 60L)
    @Override
    protected void doMergeFile(MergeFileContext context) throws IOException {
        String cacheKey = getCacheKey(context.getIdentifier(), context.getUserId());
        ChunkUploadEntity entity = getCache().get(cacheKey, ChunkUploadEntity.class);
        if (Objects.isNull(entity)) {
            throw new RPanBusinessException("文件分片合并失败，文件的唯一标识为：" + context.getIdentifier());
        }

        List<String> chunkPaths = context.getRealPathList();
        List<CompletedPart> completedParts = new ArrayList<>();
        if (!CollectionUtils.isEmpty(chunkPaths)) {
            completedParts = chunkPaths.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(this::analysisUrlParams)
                    .filter(Objects::nonNull)
                    .filter(jsonObject -> !jsonObject.isEmpty())
                    .map(jsonObject -> CompletedPart.builder()
                            .partNumber(jsonObject.getIntValue(PART_NUMBER_KEY))
                            .eTag(jsonObject.getString(E_TAG_KEY))
                            .build())
                    .collect(Collectors.toList());
        }

        CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                        .bucket(config.getBucketName())
                        .key(entity.getObjectKey())
                        .uploadId(entity.getUploadId())
                        .multipartUpload(CompletedMultipartUpload.builder()
                                .parts(completedParts)
                                .build())
                        .build());

        if (Objects.isNull(response)) {
            throw new RPanBusinessException("文件分片合并失败，文件的唯一标识为：" + context.getIdentifier());
        }
        getCache().evict(cacheKey);
        context.setRealPath(entity.getObjectKey());
    }

    /**
     * 读取文件内容并写入到输出流
     * 用于文件下载场景
     * 1、调用 S3 getObject API 获取文件流
     * 2、将文件流写入到响应输出流
     *
     * @param context 文件读取上下文，包含文件真实路径、输出流等信息
     * @throws IOException 文件读取异常或 S3 通信异常
     */
    @Override
    protected void doReadFile(ReadFileContext context) throws IOException {
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(config.getBucketName())
                        .key(context.getRealPath())
                        .build());
        if (Objects.isNull(response)) {
            throw new RPanBusinessException("文件读取失败，文件的名称是" + context.getRealPath());
        }
        FileUtil.writeStream2StreamNormal(response, context.getOutputStream());
    }

    /*********************************************
     * private
     ************************************************************/

    /**
     * 分片上传实体内部类
     * 用于存储分片上传的 uploadId 和 objectKey
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class ChunkUploadEntity implements Serializable {
        @Serial
        private static final long serialVersionUID = -32443597655643L;

        private String uploadId;

        private String objectKey;
    }

    /**
     * 生成分片上传缓存键
     *
     * @param identifier 文件唯一标识
     * @param userId     用户ID
     * @return 缓存键字符串
     */
    private String getCacheKey(String identifier, Long userId) {
        return String.format(CACHE_KEY_TEMPLATE, identifier, userId);
    }

    /**
     * 生成文件存储路径
     * 格式：/年/月/日/UUID.fileSuffix
     *
     * @param fileSuffix 文件后缀（如 .jpg, .pdf）
     * @return 完整的文件存储路径
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
     * 组装URL，将参数以查询字符串形式附加到基础URL后
     *
     * @param baseUrl 基础URL
     * @param params  参数字典
     * @return 组装后的完整URL
     */
    private String assembleUrl(String baseUrl, JSONObject params) {
        if (Objects.isNull(params) || params.isEmpty()) {
            return baseUrl;
        }
        StringBuilder urlStringBuffer = new StringBuilder(baseUrl);
        urlStringBuffer.append(RPanConstants.QUESTION_MARK_STR);
        List<String> paramsList = new ArrayList<>();
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
     * 从URL中提取基础路径（去除查询参数部分）
     *
     * @param url 完整URL
     * @return 基础路径部分
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
     * 获取URL参数的分隔符标记
     *
     * @param mark 分隔符（如 ? 或 & 或 =）
     * @return 正则表达式格式的分隔符
     */
    private String getSplitMark(String mark) {
        return RPanConstants.LEFT_BRACKET_STR +
                mark +
                RPanConstants.RIGHT_BRACKET_STR;
    }

    /**
     * 解析URL中的查询参数，返回JSON对象
     *
     * @param url 完整URL
     * @return 包含查询参数的JSON对象
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
     * 检查URL是否包含查询参数
     *
     * @param url URL字符串
     * @return 是否包含查询参数
     */
    private boolean checkHaveParams(String url) {
        return StringUtils.isNotBlank(url) && url.contains(RPanConstants.QUESTION_MARK_STR);
    }

    /**
     * 获取或初始化分片上传实体（使用Lua脚本保证原子性）
     * 
     * <p>
     * 使用Lua脚本实现原子性的检查和写入，避免双重检查锁定模式：
     * </p>
     * <ol>
     * <li>先从本地缓存获取，如果存在则直接返回</li>
     * <li>本地缓存不存在，初始化uploadId</li>
     * <li>使用Lua脚本原子性写入Redis：如果key不存在则写入，存在则返回已有值</li>
     * <li>如果Lua脚本返回已有值，说明其他线程已初始化，使用返回值</li>
     * <li>如果Lua脚本返回'OK'，说明当前线程写入成功</li>
     * </ol>
     *
     * <p>
     * <b>Lua脚本的优势：</b>
     * </p>
     * <ul>
     * <li>原子性：Redis单线程执行Lua脚本，不会被中断</li>
     * <li>性能：无需分布式锁，减少网络往返</li>
     * <li>简洁：一次Redis调用完成检查和写入</li>
     * </ul>
     *
     * @param filename 文件名
     * @param cacheKey 缓存键
     * @return 分片上传实体（包含uploadId和objectKey）
     */
    private ChunkUploadEntity getOrInitChunkUploadEntity(String filename, String cacheKey) {
        ChunkUploadEntity entity = getCache().get(cacheKey, ChunkUploadEntity.class);
        if (Objects.nonNull(entity)) {
            return entity;
        }

        entity = initChunkUpload(filename, cacheKey);
        String jsonValue = JSONObject.toJSONString(entity);

        String result = stringRedisTemplate.execute(
                checkAndPutRedisScript,
                Collections.singletonList(cacheKey),
                jsonValue,
                String.valueOf(3600));

        if (!"OK".equals(result) && StringUtils.isNotBlank(result)) {
            entity = JSONObject.parseObject(result, ChunkUploadEntity.class);
            getCache().put(cacheKey, entity);
        }

        return entity;
    }

    /**
     * 初始化文件分片上传
     * 1、执行初始化的请求
     * 2、保存初始化结果到缓存
     *
     * @param filename 文件名
     * @param cacheKey 缓存键
     * @return 分片上传实体
     */
    private ChunkUploadEntity initChunkUpload(String filename, String cacheKey) {
        String filePath = getFilePath(filename);
        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(config.getBucketName())
                        .key(filePath)
                        .build());
        if (Objects.isNull(response) || StringUtils.isBlank(response.uploadId())) {
            throw new RPanBusinessException("文件分片上传初始化失败");
        }
        ChunkUploadEntity entity = new ChunkUploadEntity();
        entity.setObjectKey(filePath);
        entity.setUploadId(response.uploadId());
        getCache().put(cacheKey, entity);
        return entity;
    }

    /**
     * 生成单文件上传预签名URL
     * 1、生成文件存储路径
     * 2、构建 PutObject 预签名请求
     * 3、返回预签名URL和objectKey
     *
     * @param context 预签名URL生成上下文，包含文件名、文件大小、MIME类型等信息
     * @return 预签名上传URL，格式为：uploadUrl|objectKey
     */
    @Override
    protected String doGeneratePresignedUploadUrl(GeneratePresignedUrlContext context) {
        String objectKey = getFilePath(FileUtil.getFileSuffix(context.getFilename()));

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(config.getPresignedUrlExpirationSeconds()))
                .putObjectRequest(builder -> builder
                        .bucket(config.getBucketName())
                        .key(objectKey)
                        .contentLength(context.getTotalSize())
                        .contentType(context.getContentType() != null ? context.getContentType()
                                : "application/octet-stream"))
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString() + "|" + objectKey;
    }

    /**
     * 生成分片上传初始化预签名URL
     * 1、生成文件存储路径
     * 2、构建 CreateMultipartUpload 预签名请求
     * 3、调用 S3 createMultipartUpload 获取 uploadId
     * 4、将 uploadId 和 objectKey 缓存到 Redis
     * 5、返回预签名URL、objectKey、uploadId 和 cacheKey
     *
     * @param context 分片上传初始化上下文，包含文件名、文件大小、MIME类型、用户ID等信息
     * @return 分片上传初始化预签名URL，格式为：uploadUrl|objectKey|uploadId|cacheKey
     */
    @Override
    protected String doGeneratePresignedMultipartInitUrl(GeneratePresignedMultipartUrlContext context) {
        String objectKey = getFilePath(FileUtil.getFileSuffix(context.getFilename()));

        CreateMultipartUploadPresignRequest presignRequest = CreateMultipartUploadPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(config.getPresignedUrlExpirationSeconds()))
                .createMultipartUploadRequest(builder -> builder
                        .bucket(config.getBucketName())
                        .key(objectKey)
                        .contentType(context.getContentType() != null ? context.getContentType()
                                : "application/octet-stream"))
                .build();

        PresignedCreateMultipartUploadRequest presignedRequest = s3Presigner
                .presignCreateMultipartUpload(presignRequest);

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(config.getBucketName())
                        .key(objectKey)
                        .build());

        if (Objects.isNull(response) || StringUtils.isBlank(response.uploadId())) {
            throw new RPanBusinessException("分片上传初始化失败");
        }

        ChunkUploadEntity entity = new ChunkUploadEntity();
        entity.setObjectKey(objectKey);
        entity.setUploadId(response.uploadId());
        String cacheKey = getCacheKey(response.uploadId(), context.getUserId());
        getCache().put(cacheKey, entity);

        return presignedRequest.url().toString() + "|" + objectKey + "|" + response.uploadId() + "|" + cacheKey;
    }

    /**
     * 生成分片上传预签名URL
     * 用于客户端直传单个分片到 S3
     * 1、构建 UploadPart 预签名请求
     * 2、返回预签名URL
     *
     * @param context 分片上传上下文，包含objectKey、uploadId、分片号、分片大小等信息
     * @return 分片上传预签名URL
     */
    @Override
    protected String doGeneratePresignedPartUploadUrl(GeneratePresignedPartUrlContext context) {
        UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(config.getPresignedUrlExpirationSeconds()))
                .uploadPartRequest(builder -> builder
                        .bucket(config.getBucketName())
                        .key(context.getObjectKey())
                        .uploadId(context.getUploadId())
                        .partNumber(context.getPartNumber()))
                .build();

        PresignedUploadPartRequest presignedRequest = s3Presigner.presignUploadPart(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * 完成分片上传并合并文件
     * 1、从上下文获取分片信息列表（partNumber + eTag）
     * 2、构建 CompletedPart 列表
     * 3、调用 S3 completeMultipartUpload API 合并分片
     * 4、清理缓存中的上传上下文
     *
     * @param context 完成分片上传上下文，包含objectKey、uploadId、分片信息列表等
     * @throws IOException 文件合并异常或 S3 通信异常
     */
    @Override
    protected void doCompleteMultipartUpload(CompleteMultipartUploadContext context) throws IOException {
        List<CompletedPart> completedParts = new ArrayList<>();
        if (!CollectionUtils.isEmpty(context.getParts())) {
            completedParts = context.getParts().stream()
                    .map(part -> CompletedPart.builder()
                            .partNumber(part.getPartNumber())
                            .eTag(part.getETag())
                            .build())
                    .collect(Collectors.toList());
        }

        CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                        .bucket(config.getBucketName())
                        .key(context.getObjectKey())
                        .uploadId(context.getUploadId())
                        .multipartUpload(CompletedMultipartUpload.builder()
                                .parts(completedParts)
                                .build())
                        .build());

        if (Objects.isNull(response)) {
            throw new RPanBusinessException("完成分片上传失败，文件合并失败");
        }

        String cacheKey = getCacheKey(context.getUploadId(), context.getUserId());
        getCache().evict(cacheKey);
    }

    /**
     * 查询已上传的分片列表
     * 调用 S3 ListParts API 获取已上传的分片编号
     *
     * @param context 查询已上传分片上下文，包含objectKey、uploadId等信息
     * @return 已上传的分片编号列表
     */
    @Override
    protected java.util.List<Integer> doListUploadedParts(ListUploadedPartsContext context) {
        java.util.List<Integer> uploadedParts = new ArrayList<>();

        ListPartsResponse response = s3Client.listParts(ListPartsRequest.builder()
                .bucket(config.getBucketName())
                .key(context.getObjectKey())
                .uploadId(context.getUploadId())
                .build());

        if (Objects.nonNull(response) && !CollectionUtils.isEmpty(response.parts())) {
            uploadedParts = response.parts().stream()
                    .map(part -> part.partNumber())
                    .collect(Collectors.toList());
        }

        return uploadedParts;
    }
}
