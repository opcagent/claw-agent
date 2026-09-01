package com.claw.agent.config.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 客户端 IP 解析过滤器：把访问者 IP 写入 Reactor 上下文，供全链路读取。
 * <p>
 * 解析优先级：{@code X-Forwarded-For} 首段（多级代理时首段才是真实客户端）
 * → {@code X-Real-IP} → TCP 对端地址。
 * <p>
 * WebFlux 无 ServletRequest，阻塞业务线程也拿不到请求对象，故统一走
 * Reactor 上下文传递：业务侧（{@code ReactiveSupport} / 匿名接口）
 * 通过 {@code deferContextual} 读取 {@link #CONTEXT_KEY} 后写入
 * {@code IpContextHolder} 供日志落库使用。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClientIpFilter implements WebFilter {

    /** Reactor 上下文中的 IP 键 */
    public static final String CONTEXT_KEY = "clientIp";

    private static final String HEADER_FORWARDED = "X-Forwarded-For";
    private static final String HEADER_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    /**
     * 解析客户端 IP 并注入上下文（解析不到时不写键，读取方用 getOrDefault 兜底）。
     *
     * @param exchange 请求交换对象
     * @param chain    过滤器链
     * @return 放行后的完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = resolveIp(exchange.getRequest());
        if (ip == null) {
            return chain.filter(exchange);
        }
        return chain.filter(exchange).contextWrite(ctx -> ctx.put(CONTEXT_KEY, ip));
    }

    /**
     * 按代理优先级解析真实客户端 IP。
     *
     * @param request 当前请求
     * @return 客户端 IP；无法解析时返回 null
     */
    private String resolveIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst(HEADER_FORWARDED);
        if (StringUtils.hasText(forwarded)) {
            // 多级代理时为「客户端,代理1,...」，取首段
            String first = forwarded.split(",")[0].trim();
            if (isValid(first)) {
                return normalizeIp(first);
            }
        }
        String realIp = request.getHeaders().getFirst(HEADER_REAL_IP);
        if (isValid(realIp)) {
            return normalizeIp(realIp.trim());
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return null;
        }
        String ip = remoteAddress.getAddress().getHostAddress();
        return normalizeIp(ip);
    }

    /** 头值有效性校验（部分代理会透传字面量 unknown） */
    private boolean isValid(String value) {
        return StringUtils.hasText(value) && !UNKNOWN.equalsIgnoreCase(value.trim());
    }

    /**
     * 标准化 IP 地址格式：
     * - IPv6 本地回环 ::1 → 127.0.0.1
     * - IPv6 映射的 IPv4 ::ffff:192.168.1.1 → 192.168.1.1
     * - 其他 IPv6 地址保持原样（生产环境通常不会暴露完整 IPv6）
     *
     * @param ip 原始 IP 字符串
     * @return 标准化后的 IP
     */
    private String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return ip;
        }
        
        String normalized = ip.trim();
        
        // IPv6 本地回环 ::1 → 127.0.0.1
        if ("::1".equals(normalized) || "0:0:0:0:0:0:0:1".equals(normalized)) {
            return "127.0.0.1";
        }
        
        // IPv6 映射的 IPv4 地址 ::ffff:192.168.1.1 → 192.168.1.1
        if (normalized.startsWith("::ffff:")) {
            return normalized.substring(7); // 去掉 "::ffff:" 前缀
        }
        
        // 其他情况保持原样（包括标准 IPv4 和完整 IPv6）
        return normalized;
    }
}
