package com.claw.agent.config.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流器：用户级 QPS + 全局并发 SSE 流数量保护。
 * <p>
 * 两层防护：
 * <ul>
 *   <li><b>用户级</b>：滑动窗口限流，防止单用户刷爆模型 API 额度（默认 10 次/分钟）</li>
 *   <li><b>全局级</b>：并发 SSE 流上限，防止系统过载（默认 50 路并发流）</li>
 * </ul>
 * <p>
 * 纯内存实现（无需 Redis）：限流是进程级保护，多实例部署时每个实例独立限流，
 * 总限流能力 = 单实例限制 × 实例数。对于中小企业规模（2-4 实例）足够用。
 */
@Slf4j
@Component
public class RateLimiter {

    /** 用户级滑动窗口时长（毫秒）：1 分钟 */
    private static final long WINDOW_MS = 60_000L;

    /** 用户级滑动窗口最大请求数 */
    private static final int USER_MAX_REQUESTS = 10;

    /** 全局并发 SSE 流上限 */
    private static final int GLOBAL_MAX_CONCURRENT = 50;

    /** 用户名 → 请求时间戳队列（滑动窗口） */
    private final Map<String, Deque<Long>> userWindows = new ConcurrentHashMap<>();

    /** 当前全局并发 SSE 流数量 */
    private final AtomicInteger globalConcurrent = new AtomicInteger(0);

    /**
     * 检查用户级限流：滑动窗口内请求次数是否超限。
     *
     * @param username 用户名
     * @return true 允许通过；false 已超限（应拒绝）
     */
    public boolean tryAcquireUser(String username) {
        if (username == null) return false;
        // 清理过期空条目，防止长期运行导致 userWindows 无限膨胀
        Deque<Long> existing = userWindows.get(username);
        if (existing != null) {
            synchronized (existing) {
                long now2 = System.currentTimeMillis();
                while (!existing.isEmpty() && now2 - existing.peekFirst() > WINDOW_MS) {
                    existing.pollFirst();
                }
                if (existing.isEmpty()) {
                    userWindows.remove(username, existing);
                }
            }
        }
        long now = System.currentTimeMillis();
        Deque<Long> window = userWindows.computeIfAbsent(username, k -> new ArrayDeque<>());
        synchronized (window) {
            // 清除窗口外的过期时间戳
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= USER_MAX_REQUESTS) {
                log.warn("用户限流触发: username={}, windowSize={}, window={}ms",
                        username, window.size(), WINDOW_MS);
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /**
     * 尝试获取全局并发 SSE 流许可。
     *
     * @return true 允许；false 已达上限
     */
    public boolean tryAcquireGlobal() {
        int current = globalConcurrent.incrementAndGet();
        if (current > GLOBAL_MAX_CONCURRENT) {
            globalConcurrent.decrementAndGet();
            log.warn("全局并发限流触发: current={}, max={}", current, GLOBAL_MAX_CONCURRENT);
            return false;
        }
        return true;
    }

    /**
     * 释放全局并发 SSE 流许可（流完成/异常时调用）。
     */
    public void releaseGlobal() {
        globalConcurrent.decrementAndGet();
    }

    /**
     * 获取用户距离限流恢复的剩余秒数。
     *
     * @param username 用户名
     * @return 剩余秒数（0 表示已可请求）
     */
    public long remainingCooldownSeconds(String username) {
        if (username == null) return 0;
        Deque<Long> window = userWindows.get(username);
        if (window == null || window.isEmpty()) return 0;
        synchronized (window) {
            if (window.isEmpty()) return 0;
            long oldest = window.peekFirst();
            long elapsed = System.currentTimeMillis() - oldest;
            return Math.max(0, (WINDOW_MS - elapsed) / 1000 + 1);
        }
    }

    /**
     * 当前全局并发 SSE 流数量（监控用）。
     */
    public int getGlobalConcurrentCount() {
        return globalConcurrent.get();
    }
}
