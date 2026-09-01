package com.claw.agent.common;

/**
 * 访问者 IP 线程上下文：把 {@code ClientIpFilter} 写入 Reactor 上下文的
 * IP 桥接到阻塞业务线程（与 {@link UserContextHolder} 同生命周期管理）。
 * <p>
 * 由 {@code ReactiveSupport} 与匿名接口（登录）在切换线程池后写入，
 * 日志记录器（操作日志/登录日志）读取落库，{@code finally} 中必须清理。
 */
public final class IpContextHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private IpContextHolder() {
    }

    /**
     * 写入当前线程的访问者 IP。
     *
     * @param ip 客户端 IP（可为 null）
     */
    public static void set(String ip) {
        HOLDER.set(ip);
    }

    /**
     * 获取当前线程的访问者 IP（未设置时返回 null）。
     *
     * @return 客户端 IP 或 null
     */
    public static String getIp() {
        return HOLDER.get();
    }

    /** 清理，防止线程池复用导致 IP 串号 */
    public static void clear() {
        HOLDER.remove();
    }
}
