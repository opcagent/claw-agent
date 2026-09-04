package com.claw.agent.config.infra;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 优雅停机管理器：追踪活跃 SSE 连接数，停机信号到达后拒绝新请求并等待存量流完成。
 * <p>
 * 配合 {@code server.shutdown=graceful} 和 {@code spring.lifecycle.timeout-per-shutdown-phase} 使用：
 * <ol>
 *   <li>SIGTERM 到达 → Spring 停止接收新 HTTP 请求</li>
 *   <li>本组件标记 {@code shuttingDown=true}，新的 SSE 请求立即被拒绝（返回 503）</li>
 *   <li>等待存量 SSE 流在超时窗口内自然完成</li>
 *   <li>超时或全部完成后进程退出</li>
 * </ol>
 * 前端收到 503 后应提示用户「服务正在更新，请稍后重试」，Agent 状态已持久化到 Redis，重启后可恢复。
 */
@Slf4j
@Component
public class GracefulShutdownManager implements SmartLifecycle {

    /** 是否正在停机 */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    /** 当前活跃的 SSE 流数量 */
    private final AtomicLong activeStreams = new AtomicLong(0);

    /** 组件是否正在运行 */
    private volatile boolean running = true;

    /** 停机等待超时（毫秒），与 application.yml 的 spring.lifecycle.timeout-per-shutdown-phase 保持一致 */
    @Value("${spring.lifecycle.timeout-per-shutdown-phase:30s}")
    private java.time.Duration shutdownTimeout;

    /** shutdownTimeout 的毫秒缓存（避免每次 stop 都转换） */
    private long timeoutMs = 30_000L;

    /**
     * 注册一个新的 SSE 流。
     *
     * @return true 注册成功；false 表示正在停机，应拒绝新请求
     */
    public boolean acquire() {
        if (shuttingDown.get()) {
            log.warn("停机中，拒绝新 SSE 请求");
            return false;
        }
        long count = activeStreams.incrementAndGet();
        log.debug("SSE 流已注册，当前活跃: {}", count);
        return true;
    }

    /**
     * 释放一个 SSE 流（流完成/异常/取消时调用）。
     */
    public void release() {
        long count = activeStreams.decrementAndGet();
        log.debug("SSE 流已释放，当前活跃: {}", count);
    }

    /**
     * 当前是否正在停机。
     * <p>
     * Controller 层在创建 SSE 流之前检查此标志，停机中则返回 503。
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * 当前活跃 SSE 流数量（监控用）。
     */
    public long getActiveStreamCount() {
        return activeStreams.get();
    }

    @Override
    public void stop() {
        // 首次调用时缓存配置值
        if (shutdownTimeout != null) {
            timeoutMs = shutdownTimeout.toMillis();
        }
        log.info("优雅停机开始: 标记 shuttingDown，等待 {} 个活跃 SSE 流完成（超时 {}ms）",
                activeStreams.get(), timeoutMs);
        shuttingDown.set(true);
        // 等待存量流完成（最多等 timeout-per-shutdown-phase 配置的时间）
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (activeStreams.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        long remaining = activeStreams.get();
        if (remaining > 0) {
            log.warn("优雅停机超时，仍有 {} 个 SSE 流未完成，强制退出", remaining);
        } else {
            log.info("优雅停机完成: 所有 SSE 流已正常结束");
        }
        running = false;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 最高优先级：在其他 SmartLifecycle Bean 之前停止
        return Integer.MAX_VALUE;
    }

    @PreDestroy
    public void onDestroy() {
        log.info("应用关闭，最终活跃 SSE 流数: {}", activeStreams.get());
    }
}
