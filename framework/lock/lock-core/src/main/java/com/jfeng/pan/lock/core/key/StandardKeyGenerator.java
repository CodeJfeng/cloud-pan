package com.jfeng.pan.lock.core.key;

import cn.hutool.core.collection.CollectionUtil;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.jfeng.pan.lock.core.LockContext;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 标准的Key生成器
 */
@Component
public class StandardKeyGenerator extends AbstractKeyGenerator{

    /**
     * 标准Key的生成格式
     * 生成格式：className:methodName:parameterType1:...:value1:value2:...
     *
     * @param lockContext
     * @param keyValueMap
     * @return
     */
    @Override
    protected String doGenerateKey(LockContext lockContext, Map<String, String> keyValueMap) {
        List<String> keyList = Lists.newArrayList();
        keyList.add(lockContext.getClassName());
        keyList.add(lockContext.getMethodName());

        Class[] parameterTypes = lockContext.getParameterTypes();
        if(ArrayUtils.isNotEmpty(parameterTypes)){
            Arrays.stream(parameterTypes).forEach(parameterType ->{
                keyList.add(parameterType.toString());
            });
        }else {
            keyList.add(Void.class.toString());
        }

        Collection<String> values = keyValueMap.values();
        if (CollectionUtil.isNotEmpty(values)){
            keyList.addAll(values);
        }
        return Joiner.on(':').join(keyList);
    }
}
