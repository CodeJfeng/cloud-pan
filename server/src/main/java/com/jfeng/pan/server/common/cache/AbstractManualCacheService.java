package com.jfeng.pan.server.common.cache;

import cn.hutool.core.map.MapUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jfeng.pan.cache.core.constants.CacheConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import org.assertj.core.util.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 手动处理缓存的公用顶级父类
 *
 * @param <V>
 */
public abstract class AbstractManualCacheService<V> implements ManualCacheService<V>{

    @Autowired(required = false)
    private CacheManager cacheManager;

    private final Object lock = new Object();

    protected abstract BaseMapper<V> getBaseMapper();

    /**
     * 获取缓存实体对象
     *
     * @return
     */
    @Override
    public Cache getCache() {
        if (Objects.isNull(cacheManager)) {
            throw new RPanBusinessException("the cache manager is empty!");
        }
        return cacheManager.getCache(CacheConstants.R_PAN_CACHE_NAME);
    }


    /**
     * 根据ID查询实体（带缓存机制）
     * <p>
     * <ol>
     *   <li><b>一级检查</b>：查询缓存，如果命中直接返回</li>
     *   <li><b>二级检查</b>：如果缓存未命中，进入同步代码块，再次检查缓存（双重检查锁定）</li>
     *   <li><b>数据加载</b>：如果缓存确实不存在，查询数据库获取数据</li>
     *   <li><b>缓存回填</b>：将数据库查询结果写入缓存，便于后续快速访问</li>
     * </ol>
     *
     * 技术要点
     * <ul>
     *   <li>使用<code>双重检查锁定（Double-Checked Locking）</code>优化性能</li>
     *   <li><code>synchronized</code>关键字防止并发场景下的缓存穿透</li>
     *   <li>使用对象锁（<code>lock</code>）而非方法锁，减小锁粒度提升并发性能</li>
     * </ul>
     * 注意事项
     * <ul>
     *   <li>需要确保数据库操作和缓存操作的事务一致性</li>
     *   <li>考虑缓存雪崩和缓存击穿的防护策略</li>
     *   <li>高并发场景下可考虑使用分布式锁替代<code>synchronized</code></li>
     * </ul>
     * <p>
     *
     * @param id 实体唯一标识符，支持任何可序列化类型
     * @return 查询到的实体对象，如果不存在则返回null
     */
    @Override
    public V getById(Serializable id) {
        V result = getByCache(id);
        if (Objects.nonNull(result)){
            return result;
        }
        // 使用锁机制避免缓存击穿
        synchronized (lock){
            result = getByCache(id);
            if (Objects.nonNull(result)){
                return result;
            }
            result = getByDB(id);
            if(Objects.nonNull(result)){
                putCache(id, result);
            }
        }
        return result;
    }


    /**
     * 根据ID更新缓存信息
     *
     * @param id
     * @param entity
     * @return
     */
    @Override
    public boolean updateById(Serializable id, V entity) {
        int rowNum = getBaseMapper().updateById(entity);
        removeCache(id);
        return rowNum == 1;
    }

    /**
     * 根据ID删除缓存信息
     *
     * @param id
     * @return
     */
    @Override
    public boolean removeById(Serializable id) {
        int rowNum = getBaseMapper().deleteById(id);
        removeCache(rowNum);
        return rowNum==1;
    }

    /**
     * 根据id集合查询实体记录列表
     *
     * @param ids
     * @return
     */
    @Override
    public List<V> getByIds(Collection<? extends Serializable> ids) {
        if (CollectionUtils.isEmpty(ids)){
            return Lists.newArrayList();
        }
        List<V> result = ids.stream().map(this::getById).toList();
        return result;
    }

    /**
     * 批量更新实体记录
     *
     * @param entityMap
     * @return
     */
    @Override
    public boolean updateByIds(Map<? extends Serializable, V> entityMap) {
        if(MapUtil.isEmpty(entityMap)){
            return false;
        }
        for (Map.Entry<? extends Serializable, V> entry : entityMap.entrySet()) {
            if(!updateById(entry.getKey(), entry.getValue())){
                return false;
            }
        }
        return true;
    }

    /**
     * 批量删除实体记录
     *
     * @param ids
     * @return
     */
    @Override
    public boolean removeByIds(Collection<? extends Serializable> ids) {
        if(CollectionUtils.isEmpty(ids)){
            return false;
        }
        for (Serializable id : ids) {
            if(!removeById(id)){
                return false;
            }
        }
        return true;
    }


    /***************************************** private ***************************************************/

    /**
     * 删除ID的缓存信息
     * @param id
     */
    private void removeCache(Serializable id) {
        String cacheKey = getCacheKey(id);
        Cache cache = getCache();
        if(Objects.isNull(cache)){
            return;
        }
        cache.evict(cacheKey);
    }

    /**
     * 将实体信息保存到缓存中
     * @param id
     * @param entity
     */
    private void putCache(Serializable id, V entity) {
        if(Objects.isNull(entity)){
            return;
        }
        String cacheKey = getCacheKey(id);
        Cache cache = getCache();
        if(Objects.isNull(cache)){
            return;
        }
        cache.put(cacheKey, entity);
    }

    /**
     * 根据ID从缓冲中查询对应的实体信息
     * @param id
     * @return
     */
    private V getByCache(Serializable id) {
        String cacheKey = getCacheKey(id);
        Cache cache = getCache();
        if(Objects.isNull(cache)){
            return null;
        }
        Cache.ValueWrapper valueWrapper = cache.get(cacheKey);
        if(Objects.isNull(valueWrapper)){
            return null;
        }
        return (V) valueWrapper.get();
    }

    /**
     * 生成对应的cacheKey
     * @param id
     * @return
     */
    private String getCacheKey(Serializable id) {
        return String.format(getKeyFormat(), id);
    }

    /**
     * 根据主键ID查询对应的实体信息
     * @param id
     * @return
     */
    private V getByDB(Serializable id) {
        return getBaseMapper().selectById(id);
    }
}
