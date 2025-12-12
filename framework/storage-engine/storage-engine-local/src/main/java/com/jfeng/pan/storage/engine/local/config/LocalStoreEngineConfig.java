package com.jfeng.pan.storage.engine.local.config;

import com.jfeng.pan.core.utils.FileUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "com.jfeng.pan.storage.engine.local")
@Data
public class LocalStoreEngineConfig {

    /**
     * 实际存放文件的地址前缀
     */
    private String  rootFilePath = FileUtil.generateDefaultStoreFileRealPath();
}
