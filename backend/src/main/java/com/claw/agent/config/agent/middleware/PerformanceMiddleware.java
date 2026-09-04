package com.claw.agent.config.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.*;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控中间件：记录 Agent 各阶段执行耗时与模型调用次数。
 * <p>
 * 监控维度：
 * <ul>
 *   <li>onAgent：单次对话回合总耗时（从用户消息到最终回复完成）</li>
 *   <li>onReasoning：推理阶段耗时（模型思考 + 工具选择）</li>
 *   <li>onActing：执行阶段耗时（工具调用 + 结果处理）</li>
 *   <li>onModelCall：单次模型调用耗时 + 回合内累计调用次数</li>
 * </ul>
 * 日志级别：INFO 输出关键耗时指标，便于排查慢请求与性能瓶颈。
 */
@Slf4j
public class PerformanceMiddleware implements MiddlewareBase {

    /** 慢请求阈值（毫秒）：超过此值的请求以 WARN 级别输出，便于快速定位性能问题 */
    private static final long SLOW_THRESHOLD_MS = 30_000;

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                    java.util.function.Function<AgentInput, Flux<AgentEvent>> next) {
        long startMs = System.currentTimeMillis();
        // 用 AtomicLong 在 lambda 中累计模型调用次数
        AtomicLong modelCallCount = new AtomicLong(0);
        ctx.put("_perf_model_calls", modelCallCount);

        return next.apply(input)
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    long calls = modelCallCount.get();
                    String userId = ctx.getUserId();
                    if (elapsed > SLOW_THRESHOLD_MS) {
                        log.warn("[性能] 慢请求: user={}, elapsed={}ms, modelCalls={}, threshold={}ms",
                                userId, elapsed, calls, SLOW_THRESHOLD_MS);
                    } else {
                        log.info("[性能] 对话完成: user={}, elapsed={}ms, modelCalls={}",
                                userId, elapsed, calls);
                    }
                })
                .doOnError(e -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.warn("[性能] 对话异常: user={}, elapsed={}ms, error={}",
                            ctx.getUserId(), elapsed, e.getMessage());
                });
    }

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext ctx, ReasoningInput input,
                                        java.util.function.Function<ReasoningInput, Flux<AgentEvent>> next) {
        long startMs = System.currentTimeMillis();
        return next.apply(input)
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    if (elapsed > 5000) {
                        log.info("[性能] 推理耗时较长: user={}, elapsed={}ms", ctx.getUserId(), elapsed);
                    } else {
                        log.debug("[性能] 推理耗时: user={}, elapsed={}ms", ctx.getUserId(), elapsed);
                    }
                });
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx, ActingInput input,
                                     java.util.function.Function<ActingInput, Flux<AgentEvent>> next) {
        long startMs = System.currentTimeMillis();
        return next.apply(input)
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    if (elapsed > 10_000) {
                        log.info("[性能] 执行耗时较长: user={}, elapsed={}ms", ctx.getUserId(), elapsed);
                    } else {
                        log.debug("[性能] 执行耗时: user={}, elapsed={}ms", ctx.getUserId(), elapsed);
                    }
                });
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        java.util.function.Function<ModelCallInput, Flux<AgentEvent>> next) {
        long startMs = System.currentTimeMillis();
        // 累计模型调用次数
        Object counter = ctx.get("_perf_model_calls");
        if (counter instanceof AtomicLong atomicLong) {
            atomicLong.incrementAndGet();
        }

        return next.apply(input)
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.debug("[性能] 模型调用: user={}, elapsed={}ms, messages={}",
                            ctx.getUserId(), elapsed, input.messages().size());
                });
    }
}
