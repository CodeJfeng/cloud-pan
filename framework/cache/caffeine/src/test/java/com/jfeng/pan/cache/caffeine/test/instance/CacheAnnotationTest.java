package com.jfeng.pan.cache.caffeine.test.instance;

import com.jfeng.pan.cache.core.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Cache 测试注解实体
 */
@Component
@Slf4j
public class CacheAnnotationTest {

    /**
     * 测试自适应缓存注解
     * 设置cache 多级缓存查询
     * id设置从1-All， 当id = -1查不到数据，填充不到缓存，就每次穿透数据库
     * 后续改进使用布隆过滤器过滤这种缓存穿透的场景
     * @param name
     * @return
     */
    @Cacheable(cacheNames = CacheConstants.R_PAN_CACHE_NAME, key = "#name", sync = true)
    public String testCache(String name){
        log.info("call com.jfeng.pan.cache.caffeine.test.instance.cacheAnnotation Test, params:", name);
        return "hello" + name;
    }
}
