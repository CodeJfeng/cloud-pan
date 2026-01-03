package com.jfeng.pan.server.common.utils;

import com.jfeng.pan.core.constants.RPanConstants;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * 分享ID存储工具类
 * <p>
 * 基于ThreadLocal实现的用户上下文信息存储，用于在当前线程中传递分享ID信息
 * </p>
 *
 * @author jfeng
 * @since 1.0.0
 * @version 1.0.0
 */
public final class ShareIdUtil {

    /**
     * <p>
     * ThreadLocal实例，用于存储当前线程的用户ID
     * 采用静态final修饰，确保全局唯一实例
     * </p>
     */
    private static final ThreadLocal<Long> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 私有构造方法，防止工具类被实例化
     */
    private ShareIdUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * <p>
     * 设置当前线程的分享ID
     * 该方法通常在分享文件访问时，身份认证通过后调用TOKEN，将分享ID存储到当前线程的上下文中
     * </p>
     *
     * @param shareId 分享文件ID，不能为null且必须大于0
     * @throws IllegalArgumentException 当shareId为null或小于等于0时抛出
     * @throws IllegalStateException 当当前线程已存在分享ID时抛出（避免覆盖）
     *
     */
    public static void set(@NonNull Long shareId) {
        if (shareId <= 0) {
            throw new IllegalArgumentException("用户ID必须大于0: " + shareId);
        }

        // 检查是否已设置（避免意外覆盖）
        Long existingUserId = THREAD_LOCAL.get();
        if (Objects.nonNull(existingUserId) && !existingUserId.equals(shareId)) {
            throw new IllegalStateException(
                    String.format("当前线程已存在分享ID[%s]，无法设置为[%s]", existingUserId, shareId)
            );
        }
        THREAD_LOCAL.set(shareId);
    }

    /**
     * <p>
     * 获取当前线程的分享文件ID
     * 如果当前线程未设置分享文件ID，返回系统默认的零值标识
     * </p>
     *
     * @return 当前线程的分享文件ID，如果未设置则返回 {@link RPanConstants#ZERO_LONG}
     */
    @NonNull
    public static Long get() {
        Long shareId = THREAD_LOCAL.get();
        return Objects.nonNull(shareId) ? shareId : RPanConstants.ZERO_LONG;
    }

}