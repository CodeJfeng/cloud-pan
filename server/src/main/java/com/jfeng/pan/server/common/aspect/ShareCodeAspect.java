package com.jfeng.pan.server.common.aspect;

import com.jfeng.pan.cache.core.constants.CacheConstants;
import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.response.ResponseCode;
import com.jfeng.pan.core.utils.JwtUtil;
import com.jfeng.pan.server.common.annotation.LoginIgnore;
import com.jfeng.pan.server.common.utils.ShareIdUtil;
import com.jfeng.pan.server.common.utils.UserIdUtil;
import com.jfeng.pan.server.modules.share.constants.ShareConstants;
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
 * 统一的分享文件的分享码校验切面逻辑实现类
 */
@Component
@Aspect
@Slf4j
public class ShareCodeAspect {

    /**
     * 登录认证参数名称
     */
    private static final String SHARE_CODE_AUTH_PARAM_NAME = "shareToken";

    /**
     * 请求头登录认证Key
     */
    private static final String SHARE_CODE_AUTH_REQUEST_NAME = "Share-Token";

    /**
     * 切点表达式——自定义注解的切点增强
     */
    private static final String POINT_CUT = "@annotation(com.jfeng.pan.server.common.annotation.NeedShareCode)";


    /**
     * 切点模板方法
     */
    @Pointcut(value = POINT_CUT)
    public void shareCodeAuth(){

    }

    /**
     * 切点的环绕增强逻辑
     * 1、需要判断需不需要校验分享码TOKEN信息
     * 2、校验登录信息：
     *  a：获取token 从请求头或者参数
     *  b：解析token
     *  c：解析的shareId存入线程的上下文、供下游使用
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("shareCodeAuth()")
    public Object shareCodeAuthAround(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
        assert servletRequestAttributes != null;
        HttpServletRequest request = servletRequestAttributes.getRequest();
        String requestURI = request.getRequestURI();
        log.info("成功拦截到请求URI");
        if(!checkAndSaveShareId(request)){
            log.warn("成功检测到请求，URI为{}，检测到用户的用户码失效，将跳转到分享码校验页面", requestURI);
            return R.fail(ResponseCode.ACCESS_DENIED);
        }
        log.info("成功拦截到请求，URI为{}，请求通过", requestURI);
        return joinPoint.proceed();
    }

    /**
     * <p>
     *   校验Token并提取shareId，并保存到线程上下文
     *   对安全性要求不高，采用无状态的JWT，对于分享码的校验，并没有什么退出逻辑，因此时间到了自动过期即可
     *   对于无状态的JWT，有以下的总结：
     *   <li>退出后令牌在过期前依然有效，存在安全风险。</li>
     *   <li>无法立即让一个已签发且未过期的令牌失效。</li>
     *   <li>盗用的令牌在过期前无法被废除。</li>
     *   <li>修改的方式是增加cache的白名单，当用户退出时，在cache层也进行过期，这样就可以比对redis里是否也存在在有效的token信息</li>
     * </p>
     *
     * 采用无状态的JWT
     * @param request
     * @return
     */
    private boolean checkAndSaveShareId(HttpServletRequest request) {
        String shareToken = request.getHeader(SHARE_CODE_AUTH_REQUEST_NAME);
        if(StringUtils.isBlank(shareToken)){
            shareToken = request.getParameter(SHARE_CODE_AUTH_PARAM_NAME);
        }
        if(StringUtils.isBlank(shareToken)){
            return false;
        }
        Object shareId = JwtUtil.analyzeToken(shareToken, ShareConstants.SHARE_ID);
        if(Objects.isNull(shareId)){
            return false;
        }
        saveShareId(shareId);
        return true;
    }

    /**
     * 保存分享信息shareId到线程的上下文
     * @param shareId
     */
    private void saveShareId(Object shareId) {
        ShareIdUtil.set(Long.valueOf(String.valueOf(shareId)));
    }

}
