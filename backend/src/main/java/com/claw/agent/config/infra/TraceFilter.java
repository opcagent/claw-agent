package com.claw.agent.config.infra;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路跟踪过滤器：为每个请求分配唯一 traceId，写入 Reactor 上下文并回显响应头。
 * <p>
 * MDC 日志跟踪的入口：请求携带 {@code X-Trace-Id} 头时沿用（跨服务串联场景），
 * 否则新生成。WebFlux 无 ServletRequest，阻塞业务线程拿不到请求对象，故与
 * {@link ClientIpFilter} 同走 Reactor 上下文传递；业务侧（{@code ReactiveSupport} /
 * 匿名接口 / 安全过滤器）通过 {@code deferContextual} 读取 {@link #CONTEXT_KEY}
 * 后写入 MDC（键 {@link #MDC_KEY}），日志模式以 {@code %X{traceId}} 输出，
 * 实现一次请求全链路日志可按 traceId 聚合。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter implements WebFilter {

    /** Reactor 上下文中的 traceId 键 */
    public static final String CONTEXT_KEY = "traceId";

    /** MDC 中的 traceId 键（与日志模式 {@code %X{traceId}} 保持一致） */
    public static final String MDC_KEY = "traceId";

    /** traceId 头名（请求透传 / 响应回显） */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 分配 traceId：注入上下文（全链路可读）并回显响应头（前端/上游网关排障用）。
     *
     * @param exchange 请求交换对象
     * @param chain    过滤器链
     * @return 放行后的完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = resolveTraceId(exchange);
        exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);
        return chain.filter(exchange).contextWrite(ctx -> ctx.put(CONTEXT_KEY, traceId));
    }

    /**
     * 解析 traceId：优先沿用上游透传值，否则新生成。
     *
     * @param exchange 请求交换对象
     * @return traceId（32 位十六进制，去掉连字符便于日志检索）
     */
    private String resolveTraceId(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID);
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
