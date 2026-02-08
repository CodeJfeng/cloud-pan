package com.jfeng.pan.bloom.filter.local;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * 本地的布隆过滤器配置类
 */
@Component
@ConfigurationProperties(prefix = "com.jfeng.pan.bloom.filter.local")
@Data
public class LocalBloomFilterConfig {

    private List<LocalBloomFilterConfigItem> items;


}
