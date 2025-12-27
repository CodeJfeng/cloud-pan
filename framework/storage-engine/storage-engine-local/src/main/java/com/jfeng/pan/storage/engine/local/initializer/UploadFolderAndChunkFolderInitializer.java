package com.jfeng.pan.storage.engine.local.initializer;

import cn.hutool.core.io.FileUtil;
import com.jfeng.pan.storage.engine.local.config.LocalStoreEngineConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 初始化上传文件根目录和文件分片存储根目录的初始化器
 */
@Component
@Slf4j
public class UploadFolderAndChunkFolderInitializer implements CommandLineRunner {

    @Autowired
    private LocalStoreEngineConfig config;


    @Override
    public void run(String... args) throws Exception {
        FileUtil.mkdir(config.getRootFilePath());
        log.info("the root file path has been created!");
        FileUtil.mkdir(config.getRootFileChunkPath());
        log.info("the root file chunk path has been created!");
    }
}
