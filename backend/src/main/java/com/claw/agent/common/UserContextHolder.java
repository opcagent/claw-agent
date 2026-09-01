package com.claw.agent.common;

import com.claw.agent.security.LoginUser;

/**
 * 阻塞线程上的当前用户上下文（ThreadLocal）。
 * <p>
 * WebFlux 的响应式安全上下文无法在 boundedElastic 阻塞线程中直接访问，
 * {@code ReactiveSupport} 在执行阻塞业务逻辑前写入、执行后清理，
 * 供 MyBatis Plus 审计字段填充（创建人/修改人）等场景取用。
 */
public final class UserContextHolder {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    /** 写入当前用户（业务逻辑执行前调用） */
    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 获取当前用户（未设置时返回 null） */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 获取当前用户名（未登录场景返回 null） */
    public static String getUsername() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    /** 获取当前用户ID（未登录或旧版 token 无此声明时返回 null） */
    public static String getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /** 清理（业务逻辑执行后必须调用，防止线程复用串数据） */
    public static void clear() {
        HOLDER.remove();
    }
}
