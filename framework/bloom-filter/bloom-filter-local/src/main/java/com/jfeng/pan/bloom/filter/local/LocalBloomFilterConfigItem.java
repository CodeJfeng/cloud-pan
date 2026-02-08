package com.jfeng.pan.bloom.filter.local;

import lombok.Data;

/**
 * 本地的布隆过滤器单体配置类
 */
@Data
public class LocalBloomFilterConfigItem {

    /**
     * 布隆过滤器的名称
     */
    private String name;

    /**
     * 数据通道的名称
     */
    private String funnelTypeName = FunnelTypeEnum.LONG.name();

    /**
     * 数据通道的长度
     */
    private long expectedInsertions = 1000000L;

    /**
     * 误判率
     */
    private double fpp = 0.01;
}
