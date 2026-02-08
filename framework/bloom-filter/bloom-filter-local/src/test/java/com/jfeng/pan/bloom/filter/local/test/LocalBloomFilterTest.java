package com.jfeng.pan.bloom.filter.local.test;

import com.jfeng.pan.bloom.filter.core.BloomFilter;
import com.jfeng.pan.bloom.filter.local.LocalBloomFilterManager;
import com.jfeng.pan.core.constants.RPanConstants;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes =  LocalBloomFilterTest.class)
@RunWith(SpringRunner.class)
@ComponentScan(basePackages = {
        RPanConstants.BASE_COMPONENT_SCAN_PATH + ".bloom.filter.local"
})
@EnableConfigurationProperties  // ✅ 启用配置属性支持
@Slf4j
public class LocalBloomFilterTest {

    @Autowired
    private LocalBloomFilterManager manager;

    /**
     * 测试本地的布隆过滤器
     */
    @Test
    public void localBloomFilterTest() {
        log.info("--------------- start --------------------");
        BloomFilter<Integer> bloomFilter = manager.getFilter("test");

        // 添加100万个元素
        for (int i = 0; i < 1000000; i++) {
            bloomFilter.put(i);
        }

        // 测试误判率（这些元素肯定不存在）
        long failNum = 0;
        for (int i = 1000000; i < 1100000; i++) {
            if (bloomFilter.mightContain(i)) {
                failNum++;
            }
        }

        log.info("test num {}, fail num {}", 100000, failNum);
    }
}