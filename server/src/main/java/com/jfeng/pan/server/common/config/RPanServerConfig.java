package com.jfeng.pan.server.common.config;

import com.jfeng.pan.core.constants.RPanConstants;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "com.imooc.pan.server")
@Data
public class RPanServerConfig {

    @Value("${server.port}")
    private Integer serverPort;

    /**
     * 文件分片的过期天数
     */
    private Integer chunkFileExpirationDays = RPanConstants.ONE_INT;

    /**
     * 分享链接的前缀
     */
    private String sharePrefix;

    /**
     * 根据Spring bean的生命周期进行注入
     */
    @PostConstruct
    public void init() {
        this.sharePrefix = "http://127.0.0.1:" + this.serverPort + "/share/";
    }

}
