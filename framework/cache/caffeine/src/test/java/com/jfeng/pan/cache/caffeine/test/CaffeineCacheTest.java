package com.jfeng.pan.cache.caffeine.test;

import cn.hutool.core.lang.Assert;
import com.jfeng.pan.cache.caffeine.test.config.CaffeineCacheConfig;
import com.jfeng.pan.cache.caffeine.test.instance.CacheAnnotationTest;
import com.jfeng.pan.cache.core.constants.CacheConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Caffeine缓存单元测试
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes= CaffeineCacheConfig.class)
public class CaffeineCacheTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheAnnotationTest cacheAnnotationTest;


    /**
     * 简单测试CacheManager的功能
     * 以及简单获取Cache对象的功能
     */
    @Test
    public void caffeineCacheManagerTest() {
        Cache cache = cacheManager.getCache(CacheConstants.R_PAN_CACHE_NAME);
        Assert.notNull(cache);
        cache.put("key1", "value1");
        String value2 =  cache.get("key1", String.class);
        Assert.isTrue("value1".equals(value2));
    }

    /**
     * 测试Cache注解是否生效
     */
    @Test
    public void caffeineCacheAnnotationTest() {
        for(int i = 0 ; i < 2 ; i++){
            cacheAnnotationTest.testCache("jfeng");
        }
    }
}
