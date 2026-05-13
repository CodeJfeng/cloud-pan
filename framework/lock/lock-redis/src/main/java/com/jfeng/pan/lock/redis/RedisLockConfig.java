package com.jfeng.pan.lock.redis;

import com.jfeng.pan.lock.core.LockConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;

@Slf4j
@SpringBootConfiguration
public class RedisLockConfig {

    @Bean
    public LockRegistry redisRegistry(RedisConnectionFactory redisConnectionFactory) {
        RedisLockRegistry redisLockRegistry = new RedisLockRegistry(
                redisConnectionFactory,
                LockConstants.R_PAN_LOCK,
                60000L);

        log.info("redis lock is loaded successfully!");
        return redisLockRegistry;
    }
}
