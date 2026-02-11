package com.jfeng.pan.lock.zookeeper;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.integration.zookeeper.config.CuratorFrameworkFactoryBean;
import org.springframework.integration.zookeeper.lock.ZookeeperLockRegistry;

/**
 * 基于ZK的分布式锁配置类
 */
@SpringBootConfiguration
@Slf4j
public class ZooKeeperLockConfig {

    @Autowired
    private ZooKeeperLockProperties properties;

    /**
     * 配置ZK的客户端
     * @return
     */
    @Bean
    public CuratorFrameworkFactoryBean curatorFrameworkFactoryBean(){
        return new CuratorFrameworkFactoryBean(properties.getHost());
    }

    /**
     * 配置ZK的分布式锁的注册器
     * @param curatorFramework
     * @return
     */
    @Bean
    public LockRegistry zooKeeperLockRegistry(CuratorFramework curatorFramework){
        ZookeeperLockRegistry zookeeperLockRegistry = new ZookeeperLockRegistry(curatorFramework);
        log.info("the ZooKeeper lock is loaded successfully!");
        return zookeeperLockRegistry;
    }


}
