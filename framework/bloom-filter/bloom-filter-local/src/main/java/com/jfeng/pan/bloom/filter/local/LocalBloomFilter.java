package com.jfeng.pan.bloom.filter.local;

import com.google.common.hash.Funnel;
import com.jfeng.pan.bloom.filter.core.BloomFilter;
import org.springframework.stereotype.Component;

/**
 * 本地实现的布隆过滤器
 *
 * @param <T>
 */
public class LocalBloomFilter<T> implements BloomFilter<T> {

    /**
     * 布隆过滤器的实现
     * 委托Google的工具类
     */
    private com.google.common.hash.BloomFilter delegate;

    /**
     * 数据通道的类型
     */
    private Funnel funnel;

    /**
     * 数据通道的长度
     */
    private long expectedInsertions;

    /**
     * 误判率
     */
    private double fpp;

    public LocalBloomFilter(Funnel funnel, long expectedInsertions, double fpp) {
        this.funnel = funnel;
        this.expectedInsertions = expectedInsertions;
        this.fpp = fpp;
        this.delegate = com.google.common.hash.BloomFilter.create(funnel, expectedInsertions, fpp);
    }

    /**
     * 放入元素
     *
     * @param object
     * @return
     */
    @Override
    public boolean put(T object) {
        return delegate.put(object);
    }

    /**
     * 判断元素是不是可能存在
     *
     * @param object
     * @return
     */
    @Override
    public boolean mightContain(T object) {
        return delegate.mightContain(object);
    }

    /**
     * 清空过滤器
     *
     * @return
     */
    @Override
    public void clear() {
        this.delegate = com.google.common.hash.BloomFilter.create(funnel, expectedInsertions, fpp);
    }
}
