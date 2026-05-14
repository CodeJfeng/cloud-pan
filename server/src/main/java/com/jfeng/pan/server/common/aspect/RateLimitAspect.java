package com.jfeng.pan.server.common.aspect;

import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.server.common.annotation.RateLimit;
import com.jfeng.pan.server.common.utils.HttpUtil;
import com.jfeng.pan.server.common.utils.UserIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 * 限流切面
 * 基于 Redis + Lua 脚本实现分布式限流
 * 使用滑动窗口算法，精确控制请求频率
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private HttpServletRequest request;

    /**
     * 滑动窗口限流 Lua 脚本
     * 从 resources/lua/rate_limit_sliding_window.lua 加载
     */
    private DefaultRedisScript<Long> rateLimitScript;

    /**
     * 初始化 Lua 脚本
     */
    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setResultType(Long.class);
        rateLimitScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/rate_limit_sliding_window.lua")));
        log.info("滑动窗口限流 Lua 脚本加载成功");
    }

    /**
     * 环绕通知：拦截标注了 @RateLimit 注解的方法
     *
     * @param point     连接点
     * @param rateLimit 限流注解
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        String key = generateKey(point, rateLimit);
        long now = System.currentTimeMillis();

        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(rateLimit.permits()),
                String.valueOf(rateLimit.seconds()),
                String.valueOf(now));

        if (result == null || result == 0) {
            log.warn("接口限流触发，key: {}, 限制: {}/{}s", key, rateLimit.permits(), rateLimit.seconds());
            throw new RPanBusinessException(rateLimit.message());
        }

        return point.proceed();
    }

    /**
     * 生成限流键
     * 格式：rate_limit:{limitType}:{key}:{identifier}
     *
     * @param point     连接点
     * @param rateLimit 限流注解
     * @return 限流键
     */
    private String generateKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder("rate_limit:");

        String keyPrefix = rateLimit.key();
        if (keyPrefix.isEmpty()) {
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();
            keyPrefix = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }
        keyBuilder.append(keyPrefix).append(":");

        switch (rateLimit.limitType()) {
            case USER:
                try {
                    Long userId = UserIdUtil.getRequired();
                    keyBuilder.append("user:").append(userId);
                } catch (Exception e) {
                    keyBuilder.append("user:anonymous");
                }
                break;
            case IP:
                String ip = HttpUtil.getIpAddress(request);
                keyBuilder.append("ip:").append(ip);
                break;
            case GLOBAL:
                keyBuilder.append("global");
                break;
            default:
                keyBuilder.append("global");
        }

        return keyBuilder.toString();
    }
}
