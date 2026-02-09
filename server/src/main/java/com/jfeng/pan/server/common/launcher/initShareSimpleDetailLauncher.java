package com.jfeng.pan.server.common.launcher;

import cn.hutool.core.collection.CollectionUtil;
import com.jfeng.pan.bloom.filter.core.BloomFilter;
import com.jfeng.pan.bloom.filter.core.BloomFilterManager;
import com.jfeng.pan.server.modules.share.service.IShareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简单分享详情布隆过滤器初始化器
 */
@Slf4j
@Component
public class initShareSimpleDetailLauncher implements CommandLineRunner {

    @Autowired
    private BloomFilterManager manager;

    @Autowired
    private IShareService iShareService;

    private static final String BLOOM_FILTER_NAME = "SHARE_SIMPLE_DETAIL";

    @Override
    public void run(String... args) throws Exception {
        log.info("start init ShareSimpleDetailBloomFilter...");
        BloomFilter<Long> bloomFilter = manager.getFilter(BLOOM_FILTER_NAME);
        if (Objects.isNull(bloomFilter)){
            log.info("the bloomFilter named {} is null, give up init...", BLOOM_FILTER_NAME);
            return;
        }
        bloomFilter.clear();

        List<Long> shareIdList;
        long startId = 0;
        long limit = 100000L;
        AtomicLong addCount = new AtomicLong(0L);
        do{
            shareIdList = iShareService.rollingQueryShareId(startId, limit);
            if(CollectionUtil.isNotEmpty(shareIdList)){
                shareIdList.forEach(shareId -> {
                    bloomFilter.put(shareId);
                    addCount.incrementAndGet();
                });
                startId = shareIdList.getLast();
            }
        }while (CollectionUtil.isNotEmpty(shareIdList));
        log.info("finish init ShareSimpleDetailBloomFilter, total set item count{}...", addCount.get());
    }
}
