package com.jfeng.pan.server.common.utils;

import com.jfeng.pan.core.constants.RPanConstants;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * 用户ID存储工具类
 * <p>
 * 基于ThreadLocal实现的用户上下文信息存储，用于在当前线程中传递用户身份信息
 * </p>
 *
 * @author jfeng
 * @since 1.0.0
 * @version 1.0.0
 */
public final class UserIdUtil {

    /**
     * ThreadLocal实例，用于存储当前线程的用户ID
     * <p>
     * 采用静态final修饰，确保全局唯一实例
     * </p>
     */
    private static final ThreadLocal<Long> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 私有构造方法，防止工具类被实例化
     */
    private UserIdUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 设置当前线程的用户ID
     * <p>
     * 该方法通常在用户身份认证通过后调用，将用户ID存储到当前线程的上下文中
     * </p>
     *
     * @param userId 用户ID，不能为null且必须大于0
     * @throws IllegalArgumentException 当userId为null或小于等于0时抛出
     * @throws IllegalStateException 当当前线程已存在用户ID时抛出（避免覆盖）
     *
     */
    public static void set(@NonNull Long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("用户ID必须大于0: " + userId);
        }

        // 检查是否已设置（避免意外覆盖）
        Long existingUserId = THREAD_LOCAL.get();
        if (Objects.nonNull(existingUserId) && !existingUserId.equals(userId)) {
            throw new IllegalStateException(
                    String.format("当前线程已存在用户ID[%s]，无法设置为[%s]", existingUserId, userId)
            );
        }
        THREAD_LOCAL.set(userId);
    }

    /**
     * 获取当前线程的用户ID
     * <p>
     * 如果当前线程未设置用户ID，返回系统默认的零值标识
     * </p>
     *
     * @return 当前线程的用户ID，如果未设置则返回 {@link RPanConstants#ZERO_LONG}
     */
    @NonNull
    public static Long get() {
        Long userId = THREAD_LOCAL.get();
        return Objects.nonNull(userId) ? userId : RPanConstants.ZERO_LONG;
    }

    /**
     * 安全获取当前线程的用户ID
     * <p>
     * 该方法在校验模式下使用，如果用户ID未设置或为无效值，将抛出异常
     * </p>
     *
     * @return 当前线程的有效用户ID
     * @throws IllegalStateException 当用户ID未设置或为无效值时抛出
     */
    @NonNull
    public static Long getRequired() {
        Long userId = THREAD_LOCAL.get();
        if (Objects.isNull(userId) || userId <= 0) {
            throw new IllegalStateException("当前线程未设置有效的用户ID");
        }
        return userId;
    }

    /**
     * 获取原始用户ID（可能为null）
     * <p>
     * 仅供内部框架使用，业务代码应使用 {@link #get()} 方法
     * </p>
     *
     * @return 当前线程的用户ID，可能为null
     */
    @Nullable
    static Long getRaw() {
        return THREAD_LOCAL.get();
    }

    /**
     * 清除当前线程的用户ID
     * <p>
     * <b>重要：</b>必须在请求处理完成后调用，防止内存泄漏和线程复用导致的数据污染
     * 建议在拦截器或过滤器的finally块中调用
     * </p>
     *
     * @example
     * <pre>{@code
     * try {
     *     // 业务处理
     *     doBusiness();
     * } finally {
     *     UserIdUtil.clear();
     * }
     * }</pre>
     */
    public static void clear() {
        THREAD_LOCAL.remove();
    }

    /**
     * 检查当前线程是否已设置用户ID
     *
     * @return 如果已设置有效用户ID返回true，否则返回false
     */
    public static boolean isSet() {
        Long userId = THREAD_LOCAL.get();
        return Objects.nonNull(userId) && userId > 0;
    }

    /**
     * 获取当前线程上下文信息（用于调试和日志记录）
     *
     * @return 包含用户ID状态的描述信息
     */
    @NonNull
    public static String getContextInfo() {
        Long userId = THREAD_LOCAL.get();
        if (Objects.isNull(userId)) {
            return "ThreadLocal用户上下文: 未设置";
        } else if (userId <= 0) {
            return String.format("ThreadLocal用户上下文: 无效ID[%s]", userId);
        } else {
            return String.format("ThreadLocal用户上下文: 用户ID[%s]", userId);
        }
    }
}