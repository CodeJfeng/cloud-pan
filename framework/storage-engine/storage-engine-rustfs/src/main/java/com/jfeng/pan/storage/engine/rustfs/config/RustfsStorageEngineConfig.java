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
import com.jfeng.pan.core.exception.RPanBusinessException;

import java.net.URI;

@Component
@Data
@ConfigurationProperties(prefix = "com.jfeng.pan.storage.engine.rustfs")
public class RustfsStorageEngineConfig {

    private static final long MIN_PART_SIZE = 5 * 1024 * 1024L;
    private static final long DEFAULT_TIMEOUT_SECONDS = 300L;

    private String endpoint;

    private String accessKeyId;

    private String accessKeySecret;

    private String bucketName;

    private Boolean autoCreateBucket = Boolean.TRUE;

    private Long minPartSize = MIN_PART_SIZE;

    private Long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

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

    public long getMinPartSize() {
        return minPartSize != null ? minPartSize : MIN_PART_SIZE;
    }
}
