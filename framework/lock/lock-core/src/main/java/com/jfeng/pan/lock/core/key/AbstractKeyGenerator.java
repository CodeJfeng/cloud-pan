package com.jfeng.pan.lock.core.key;

import com.google.common.collect.Maps;
import com.jfeng.pan.core.utils.SpElUtil;
import com.jfeng.pan.lock.core.LockContext;
import com.jfeng.pan.lock.core.annotation.Lock;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.Map;

/**
 * 锁的Key生成器的公用父类
 */
public abstract class AbstractKeyGenerator implements KeyGenerator{

    /**
     * <p>
     *     <h3>生成分布式锁的唯一Key</h3>
     *     <p>
     *         核心逻辑：基于方法参数和SpEL表达式动态生成锁Key，确保细粒度锁控制
     *         处理流程:
     *         <li>1.从@Lock注解中提取SpEL表达式定义的key模板</li>
     *         <li>2.使用SpEL解析器将表达式转换为实际值（支持方法参数、对象属性等动态取值）</li>
     *         <li>3.将解析后的键值对传递给具体实现生成最终锁Key</li>
     *     </p>
     * </p>
     *
     * @param lockContext
     * @return
     */
    @Override
    public String generateKey(LockContext lockContext) {
        Lock annotation = lockContext.getAnnotation();
        String[] keys = annotation.keys();
        Map<String, String> keyValueMap = Maps.newHashMap();
        if (ArrayUtils.isNotEmpty(keys)){
            Arrays.stream(keys).forEach(key ->{
                keyValueMap.put(key, SpElUtil.getStringValue(key, lockContext.getClassName(),
                        lockContext.getMethodName(),lockContext.getClassType(),
                        lockContext.getMethod(),lockContext.getArgs(),
                        lockContext.getParameterTypes(),lockContext.getTarget()));
            });
        }
        return doGenerateKey(lockContext, keyValueMap);
    }

    /**
     * 具体逻辑下称到子类实现
     *
     * @param lockContext
     * @param keyValueMap
     * @return
     */
    protected abstract String doGenerateKey(LockContext lockContext, Map<String, String> keyValueMap);
}
