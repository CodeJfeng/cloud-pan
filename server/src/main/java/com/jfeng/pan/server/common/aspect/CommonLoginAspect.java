package com.jfeng.pan.server.common.aspect;

import com.jfeng.pan.cache.core.constants.CacheConstants;
import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.response.ResponseCode;
import com.jfeng.pan.core.utils.JwtUtil;
import com.jfeng.pan.server.common.annotation.LoginIgnore;
import com.jfeng.pan.server.common.utils.UserIdUtil;
import com.jfeng.pan.server.modules.user.constants.UserConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 统一的登录拦截校验切面逻辑实现类
 */
@Component
@Aspect
@Slf4j
public class CommonLoginAspect {

    /**
     * 登录认证参数名称
     */
    private static final String LOGIN_AUTH_PARAM_NAME = "authorization";

    /**
     * 请求头登录认证
     */
    private static final String LOGIN_AUTH_REQUEST_NAME = "Authorization";

    /**
     * 切点表达式
     */
    private static final String POINT_CUT = "execution(* com.jfeng.pan.server.modules.*.controller..*(..))";

    @Autowired
    private CacheManager cacheManager;

    /**
     * 切点模板
     */
    @Pointcut(value = POINT_CUT)
    public void loginAuth() {

    }

    /**
     * 切点的环绕增强逻辑
     * 1、需要判断需不需要校验登录信息
     * 2、校验登录信息：
     * a：获取token 从请求头或者参数
     * b：从换从中获取token进行比对
     * c：解析token
     * d：解析的userId存入线程的上下文、供下游使用
     * 
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("loginAuth()")
    public Object loginAuthAround(ProceedingJoinPoint joinPoint) throws Throwable {
        if (checkNeedCheckLoginInfo(joinPoint)) {
            // 登录信息校验流程
            ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            HttpServletRequest request = servletRequestAttributes.getRequest();
            String requestURI = request.getRequestURI();
            log.info("成功拦截到请求URI");
            if (!checkAndSaveUserId(request) && !checkMCPAndSaveUserID(request)) {
                log.warn("成功检测到请求，URI为{}，检测到用户未登录，将跳转到登录界面", requestURI);
                return R.fail(ResponseCode.NEED_LOGIN);
            }
            log.info("成功拦截到请求，URI为{}，请求通过", requestURI);

        }
        return joinPoint.proceed();
    }

    /**
     * 校验MCP登录信息
     * 1、从请求头或者参数中获取MCP登录token
     * 2、从缓存中获取MCP登录token
     * 3、比对MCP登录token是否一致
     * 4、如果一致，将MCP登录token中的userId存入线程的上下文、供下游使用
     * 5、返回true
     * 6、返回false
     * 
     * @param request
     * @return
     */
    private boolean checkMCPAndSaveUserID(HttpServletRequest request) {
        String authorization = request.getHeader(LOGIN_AUTH_REQUEST_NAME);
        if (StringUtils.isBlank(authorization)) {
            authorization = request.getParameter(LOGIN_AUTH_PARAM_NAME);
        }
        if (StringUtils.isBlank(authorization)) {
            return false;
        }

        // 校验token是否有效 验证 Bearer 前缀为OAuth的标准
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false; // 拦截请求
        }
        String token = authorization.substring(7);
        if (StringUtils.isBlank(token)) {
            return false;
        }

        // Token 不加密，直接存进userId
        if (token.startsWith("MCP_")) {
            saveUserId(token.substring(4));
            return true;
        }
        return false;
    }

    /**
     * 校验Token并提取UserId，并保存到线程上下文
     * @param request
     * @return
     */
    private boolean checkAndSaveUserId(HttpServletRequest request) {
        String accessToken = request.getHeader(LOGIN_AUTH_REQUEST_NAME);
        if (StringUtils.isBlank(accessToken)) {
            accessToken = request.getParameter(LOGIN_AUTH_PARAM_NAME);
        }
        if (StringUtils.isBlank(accessToken)) {
            return false;
        }
        Object userId = JwtUtil.analyzeToken(accessToken, UserConstants.LOGIN_USER_ID);
        if (Objects.isNull(userId)) {
            return false;
        }
        Cache cache = cacheManager.getCache(CacheConstants.R_PAN_CACHE_NAME);
        assert cache != null;
        Cache.ValueWrapper wrapper = cache.get(UserConstants.USER_LOGIN_PREFIX + userId);
        if (Objects.isNull(wrapper) || Objects.isNull(wrapper.get())) {
            return false;
        }
        if (wrapper.get() instanceof String redisAccessToken) {
            if (accessToken.equals(redisAccessToken)) {
                saveUserId(userId);
                return true;
            }
        }
        return false;
    }

    /**
     * 保存用户信息userId到线程的上下文
     * 
     * @param userId
     */
    private void saveUserId(Object userId) {
        UserIdUtil.set(Long.valueOf(String.valueOf(userId)));
    }

    /**
     * 校验是否需要校验登录信息
     *
     * @param joinPoint
     * @return
     */
    private boolean checkNeedCheckLoginInfo(ProceedingJoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        return !method.isAnnotationPresent(LoginIgnore.class);
    }

}
