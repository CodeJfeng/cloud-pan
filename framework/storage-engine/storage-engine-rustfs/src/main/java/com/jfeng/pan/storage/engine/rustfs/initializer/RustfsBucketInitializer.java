package com.jfeng.pan.storage.engine.rustfs.initializer;

import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.storage.engine.rustfs.config.RustfsStorageEngineConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Component
@Slf4j
public class RustfsBucketInitializer implements CommandLineRunner {

    @Autowired
    private RustfsStorageEngineConfig config;

    @Autowired
    private S3Client s3Client;

    @Override
    public void run(String... args) throws Exception {
        boolean bucketExist = checkBucketExists(config.getBucketName());
        if(!bucketExist && config.getAutoCreateBucket()){
            try {
                s3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(config.getBucketName())
                        .build());
                log.info("the bucket {} have been created!", config.getBucketName());
            } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
                log.info("the bucket {} already exists!", config.getBucketName());
            }
        }

        if(!bucketExist && !config.getAutoCreateBucket()){
            throw new RPanBusinessException("the bucket " + config.getBucketName() + " is not available!");
        }

        log.info("the bucket {} have been initialized!", config.getBucketName());
    }

    private boolean checkBucketExists(String bucketName) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
