package com.jfeng.pan.bloom.filter.local;


import com.google.common.collect.Maps;
import com.jfeng.pan.bloom.filter.core.BloomFilter;
import com.jfeng.pan.bloom.filter.core.BloomFilterManager;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 本地布隆过滤器管理器
 *
 */
@Component
public class LocalBloomFilterManager implements BloomFilterManager, InitializingBean {

    @Autowired
    private LocalBloomFilterConfig config;

    /**
     * 布隆管理器的容器
     */
    private final Map<String, BloomFilter> bloomFilterContainer = Maps.newConcurrentMap();

    /**
     * 根据名称获取对应的布隆过滤器
     *
     * @param name
     * @return
     */
    @Override
    public BloomFilter getFilter(String name) {
        return bloomFilterContainer.get(name);
    }

    /**
     * 获取目前管理器中存在的布隆过滤器名称列表
     *
     * @return
     */
    @Override
    public Collection<String> getFilterNames() {
        return bloomFilterContainer.keySet();
    }

    /**
     *
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        List<LocalBloomFilterConfigItem> items = config.getItems();
        if (!CollectionUtils.isEmpty(items)) {
            items.forEach(item -> {
                String funnelName = item.getFunnelTypeName();
                FunnelTypeEnum funnelType = FunnelTypeEnum.valueOf(funnelName);
                bloomFilterContainer.put(
                        item.getName(),
                        new LocalBloomFilter(
                                funnelType.getFunnel(),
                                item.getExpectedInsertions(),
                                item.getFpp()));
            });
        }
    }
}
