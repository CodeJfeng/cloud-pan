package com.jfeng.pan.bloom.filter.local;

import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.StandardCharsets;

/**
 * 数据通道枚举类
 */
@AllArgsConstructor
@Getter
public enum FunnelTypeEnum {
    /**
     * long类型数据通道
     */
    LONG(Funnels.longFunnel()),
    /**
     * int类型数据通道
     */
    INTEGER(Funnels.integerFunnel()),
    /**
     * String类型数据通道
     */
    STRING(Funnels.stringFunnel(StandardCharsets.UTF_8));


    private Funnel funnel;
}
