package com.jfeng.pan.storage.engine.rustfs.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import com.jfeng.pan.core.exception.RPanBusinessException;

import java.net.URI;

@Component
@Data
@ConfigurationProperties(prefix = "com.jfeng.pan.storage.engine.rustfs")
public class RustfsStorageEngineConfig {
    /**
     * 最小分块大小，单位：字节
     */
    private static final long MIN_PART_SIZE = 5 * 1024 * 1024L;

    /**
     * 默认超时时间，单位：秒
     */
    private static final long DEFAULT_TIMEOUT_SECONDS = 300L;
    /**
     * 默认预签名URL过期时间，单位：秒
     */
    private static final long DEFAULT_PRESIGNED_URL_EXPIRATION_SECONDS = 900L;
    /**
     * 默认端点
     */
    private String endpoint;
    /**
     * 默认访问密钥ID
     */
    private String accessKeyId;

    /**
     * 默认访问密钥Secret
     */
    private String accessKeySecret;
    /**
     * 默认桶名称
     */
    private String bucketName;
    /**
     * 是否自动创建桶
     */
    private Boolean autoCreateBucket = Boolean.TRUE;
    /**
     * 默认最小分块大小，单位：字节
     */
    private Long minPartSize = MIN_PART_SIZE;
    /**
     * 默认超时时间，单位：秒
     */
    private Long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    /**
     * 默认预签名URL过期时间，单位：秒
     */
    private Long presignedUrlExpirationSeconds = DEFAULT_PRESIGNED_URL_EXPIRATION_SECONDS;
    /**
     * 默认公共端点
     */
    private String publicEndpoint;

    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        if (StringUtils.isAllBlank(getEndpoint(), getAccessKeyId(), getAccessKeySecret(), getBucketName())) {
            throw new RPanBusinessException("the rustfs config is missed!");
        }
        return S3Client.builder()
                .endpointOverride(URI.create(getEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(getAccessKeyId(), getAccessKeySecret())))
                .forcePathStyle(true)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner() {
        if (StringUtils.isAllBlank(getEndpoint(), getAccessKeyId(), getAccessKeySecret(), getBucketName())) {
            throw new RPanBusinessException("the rustfs config is missed!");
        }
        return S3Presigner.builder()
                .endpointOverride(URI.create(getEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(getAccessKeyId(), getAccessKeySecret())))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    public long getMinPartSize() {
        return minPartSize != null ? minPartSize : MIN_PART_SIZE;
    }

    public long getPresignedUrlExpirationSeconds() {
        return presignedUrlExpirationSeconds != null ? presignedUrlExpirationSeconds
                : DEFAULT_PRESIGNED_URL_EXPIRATION_SECONDS;
    }
}
