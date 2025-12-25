package com.jfeng.pan.storage.engine.oss.config;

import com.aliyun.oss.OSSClient;
import com.jfeng.pan.core.exception.RPanBusinessException;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * OSS文件存储引擎配置文件
 */
@Component
@Data
@ConfigurationProperties(prefix = "com.jfeng.pan.storage.engine.oss")
public class OssStorageEngineConfig {

    private String endpoint;

    private String accessKeyId;

    private String accessKeySecret;

    private String bucketName;

    private Boolean autoCreateBucket = Boolean.TRUE;

    /**
     * 注入OSS操作的客户端对象
     * @return
     */
    @Bean(destroyMethod = "shutdown")
    public OSSClient ossClient(){
        if(StringUtils.isAllBlank(getEndpoint(), getAccessKeyId(), getAccessKeySecret(), getBucketName())){
            throw new RPanBusinessException("the oss config is missed!");
        }
        return new OSSClient(getEndpoint(), getAccessKeyId(), getAccessKeySecret());
    }
}
