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
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
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

    @Lock(name = "RustfsDoStoreChunkLock", keys = { "#context.userId", "#context.identifier" }, expireSecond = 10L)
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
        ChunkUploadEntity entity = getCache().get(cacheKey, ChunkUploadEntity.class);
        if (Objects.isNull(entity)) {
            entity = initChunkUpload(context.getFilename(), cacheKey);
        }

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

    private String getCacheKey(String identifier, Long userId) {
        return String.format(CACHE_KEY_TEMPLATE, identifier, userId);
    }

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

    private String getBaseUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return RPanConstants.EMPTY_STR;
        }
        if (checkHaveParams(url)) {
            return url.split(getSplitMark(RPanConstants.QUESTION_MARK_STR))[0];
        }
        return url;
    }

    private String getSplitMark(String mark) {
        return RPanConstants.LEFT_BRACKET_STR +
                mark +
                RPanConstants.RIGHT_BRACKET_STR;
    }

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

    private boolean checkHaveParams(String url) {
        return StringUtils.isNotBlank(url) && url.contains(RPanConstants.QUESTION_MARK_STR);
    }

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
}
