package com.claw.agent.config.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型提供商熔断器：按提供商独立追踪调用失败次数，连续失败达到阈值时自动熔断。
 * <p>
 * 三态模型：
 * <ul>
 *   <li><b>CLOSED</b>（正常）：调用正常通过，失败计数累加</li>
 *   <li><b>OPEN</b>（熔断）：连续失败达到阈值，立即拒绝并返回友好提示，不发起实际调用</li>
 *   <li><b>HALF_OPEN</b>（探测）：熔断超时后放行一次请求探测恢复，成功则重置，失败则重新熔断</li>
 * </ul>
 * <p>
 * 与 AgentScope 内置重试（ExecutionConfig maxAttempts=3）互补：
 * 重试处理单次网络抖动，熔断处理提供商持续不可用（API Key 过期、额度耗尽、服务宕机）。
 * 避免提供商宕机时每次对话都等 3 次重试超时（3×120s=6 分钟）才报错。
 */
@Slf4j
@Component
public class ModelCircuitBreaker {

    /** 连续失败次数阈值：达到后触发熔断 */
    private static final int FAILURE_THRESHOLD = 5;

    /** 熔断冷却时间（秒）：超时后进入 HALF_OPEN 探测 */
    private static final int COOLDOWN_SECONDS = 60;

    /** 提供商键 → 熔断状态 */
    private final Map<String, CircuitState> states = new ConcurrentHashMap<>();

    /**
     * 检查提供商是否可用。
     * <p>
     * CLOSED → 放行；OPEN 且未超时 → 拒绝；OPEN 且已超时 → HALF_OPEN 放行探测。
     *
     * @param provider 提供商标识（如 "dashscope"、"openai"）
     * @return true 可用（放行）；false 已熔断（应拒绝）
     */
    public boolean isAvailable(String provider) {
        if (provider == null) return true;
        CircuitState state = states.get(provider);
        if (state == null) return true;
        return state.isAvailable();
    }

    /**
     * 获取熔断拒绝时的友好提示消息。
     *
     * @param provider 提供商标识
     * @return 用户可读的提示文案
     */
    public String getRejectionMessage(String provider) {
        CircuitState state = states.get(provider);
        if (state == null) return "模型服务暂时不可用";
        long remainSeconds = state.remainingCooldownSeconds();
        if (remainSeconds > 0) {
            return String.format("模型提供商 %s 连续调用失败，已临时熔断（%d 秒后自动重试），请稍后再试或切换其他模型",
                    provider, remainSeconds);
        }
        return "模型提供商 " + provider + " 暂时不可用，请稍后重试";
    }

    /**
     * 记录一次成功调用：重置失败计数，状态回到 CLOSED。
     *
     * @param provider 提供商标识
     */
    public void recordSuccess(String provider) {
        if (provider == null) return;
        CircuitState state = states.get(provider);
        if (state != null) {
            state.reset();
            log.debug("模型 {} 调用成功，熔断器重置", provider);
        }
    }

    /**
     * 记录一次失败调用：累加失败计数，达到阈值时触发熔断。
     *
     * @param provider 提供商标识
     */
    public void recordFailure(String provider) {
        if (provider == null) return;
        CircuitState state = states.computeIfAbsent(provider, k -> new CircuitState());
        int failures = state.recordFailure();
        if (failures >= FAILURE_THRESHOLD && state.trip()) {
            log.warn("模型 {} 连续失败 {} 次，触发熔断（{}s 后自动探测恢复）",
                    provider, failures, COOLDOWN_SECONDS);
        }
    }

    /**
     * 手动重置指定提供商的熔断状态（管理员切换模型配置后调用）。
     *
     * @param provider 提供商标识
     */
    public void reset(String provider) {
        CircuitState state = states.remove(provider);
        if (state != null) {
            log.info("手动重置模型 {} 熔断状态", provider);
        }
    }

    /**
     * 获取所有提供商的熔断状态快照（监控页展示）。
     */
    public Map<String, String> snapshot() {
        Map<String, String> result = new ConcurrentHashMap<>();
        states.forEach((provider, state) -> result.put(provider, state.statusString()));
        return result;
    }

    /**
     * 单个提供商的熔断状态。
     * <p>
     * 线程安全：所有字段操作在 synchronized 块内完成，状态转换由 volatile 保证可见性。
     */
    private static class CircuitState {

        private final AtomicInteger failures = new AtomicInteger(0);
        private volatile boolean open = false;
        private volatile Instant openedAt = null;

        /**
         * 检查是否可用。
         */
        boolean isAvailable() {
            if (!open) return true;
            // 熔断冷却期已过 → 进入 HALF_OPEN，放行探测
            if (Instant.now().isAfter(openedAt.plusSeconds(COOLDOWN_SECONDS))) {
                return true;
            }
            return false;
        }

        /**
         * 记录失败，返回当前失败次数。
         */
        int recordFailure() {
            return failures.incrementAndGet();
        }

        /**
         * 尝试触发熔断（CAS 语义：只有第一个到达阈值的线程能触发）。
         *
         * @return true 本次触发了熔断；false 已经熔断过
         */
        boolean trip() {
            if (open) return false;
            synchronized (this) {
                if (open) return false;
                open = true;
                openedAt = Instant.now();
                return true;
            }
        }

        /**
         * 重置状态（成功调用或手动重置时）。
         */
        void reset() {
            failures.set(0);
            open = false;
            openedAt = null;
        }

        /**
         * 距离熔断结束的剩余秒数。
         */
        long remainingCooldownSeconds() {
            if (!open || openedAt == null) return 0;
            long elapsed = Instant.now().getEpochSecond() - openedAt.getEpochSecond();
            return Math.max(0, COOLDOWN_SECONDS - elapsed);
        }

        /**
         * 状态描述字符串。
         */
        String statusString() {
            if (!open) return "CLOSED(failures=" + failures.get() + ")";
            long remain = remainingCooldownSeconds();
            if (remain > 0) return "OPEN(remain=" + remain + "s)";
            return "HALF_OPEN(probing)";
        }
    }
}
