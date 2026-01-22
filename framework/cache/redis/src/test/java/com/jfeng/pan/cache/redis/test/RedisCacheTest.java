package com.jfeng.pan.cache.redis.test;

import cn.hutool.core.lang.Assert;
import com.jfeng.pan.cache.core.constants.CacheConstants;
import com.jfeng.pan.cache.redis.test.instance.CacheAnnotationTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = {RedisCacheTest.class})
@SpringBootApplication
@RunWith(value = SpringRunner.class)
public class RedisCacheTest {
    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheAnnotationTest cacheAnnotationTest;

    /**
     * 简单测试CacheManager的功能
     * 以及简单获取Cache对象的功能
     */
    @Test
    public void redisCacheManagerTest() {
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
    public void redisCacheAnnotationTest() {
        for(int i = 0 ; i < 2 ; i++){
            cacheAnnotationTest.testCache("jfeng3");
        }
    }

}
