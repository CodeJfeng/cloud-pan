package com.jfeng.pan.server.common.config;

import com.jfeng.pan.core.constants.RPanConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "com.imooc.pan.server")
@Data
public class RPanServerConfig {

    /**
     * 文件分片的过期天数
     */
    private Integer chunkFileExpirationDays = RPanConstants.ONE_INT;
}
