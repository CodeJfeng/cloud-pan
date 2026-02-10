package com.jfeng.pan.lock.local.test.instance;

import com.jfeng.pan.lock.core.annotation.Lock;
import org.springframework.stereotype.Component;

/**
 * Lock测试实体
 */
@Component
public class LockTester {

    @Lock(name = "test",  keys = "#name", expireSecond = 10L)
    public void testLock(String name){
        System.out.println(Thread.currentThread().getName()+" get the lock.");
        String result = "hello "+ name;
        System.out.println(Thread.currentThread().getName()+" release the lock.");
    }
}
