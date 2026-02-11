package com.jfeng.pan.lock.zookeeper;

import com.jfeng.pan.lock.core.LockConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ZK的配置信息注册
 */
@Data
@Component
@ConfigurationProperties(prefix = "com.jfeng.pan.lock.zookeeper")
public class ZooKeeperLockProperties {
    /**
     * ZK链接地址，多个使用逗号隔开
     */
    private String host = "127.0.0.1:2181";

    /**
     * ZK分布式锁的根路径
     */
    private String rootPath = LockConstants.R_PAN_LOCK_PATH;

}
