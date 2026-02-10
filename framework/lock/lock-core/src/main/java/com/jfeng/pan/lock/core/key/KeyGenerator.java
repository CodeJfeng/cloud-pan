package com.jfeng.pan.lock.core.key;

import com.jfeng.pan.lock.core.LockContext;

/**
 * 锁的Key生成器顶级接口
 */
public interface KeyGenerator {
    /**
     * 生成锁的Key
     * @param lockContext
     * @return
     */
    String generateKey(LockContext lockContext);

}
