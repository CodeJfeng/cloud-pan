package com.jfeng.pan.server.modules.user.service.cache;

import com.jfeng.pan.cache.core.constants.CacheConstants;
import com.jfeng.pan.server.common.cache.AnnotationCacheService;
import com.jfeng.pan.server.modules.user.entity.RPanUser;
import com.jfeng.pan.server.modules.user.mapper.RPanUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 用户模块缓存业务处理类
 */
@Component(value = "userAnnotationCacheService")
public class UserCacheService implements AnnotationCacheService<RPanUser> {

    @Autowired
    private RPanUserMapper mapper;

    /**
     * 根据ID查询实体
     *
     * @param id
     * @return
     */
    @Cacheable(cacheNames = CacheConstants.R_PAN_CACHE_NAME, keyGenerator = "userIdKeyGenerator", sync = true)
    @Override
    public RPanUser getById(Serializable id) {
        return mapper.selectById(id);
    }

    /**
     * 根据ID更新缓存信息
     *
     * @param id
     * @param entity
     * @return
     */
    @CachePut(cacheNames = CacheConstants.R_PAN_CACHE_NAME, keyGenerator = "userIdKeyGenerator")
    @Override
    public boolean updateById(Serializable id, RPanUser entity) {
        return mapper.updateById(entity) == 1;
    }

    /**
     * 根据ID删除缓存信息
     *
     * @param id
     * @return
     */
    @CacheEvict(cacheNames = CacheConstants.R_PAN_CACHE_NAME, keyGenerator = "userIdKeyGenerator")
    @Override
    public boolean removeById(Serializable id) {
        return mapper.deleteById(id) == 1;
    }
}
