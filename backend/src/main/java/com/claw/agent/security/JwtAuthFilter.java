package com.claw.agent.security;

import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.RoleConstants;
import com.claw.agent.config.security.ClientIpFilter;
import com.claw.agent.config.infra.TraceFilter;
import com.claw.agent.service.OnlineUserTracker;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器（WebFlux 响应式版本）。
 * <p>
 * 从 Authorization: Bearer {token} 头解析并校验 JWT，
 * 校验通过后把认证信息（LoginUser：用户名+租户+角色集）写入 ReactiveSecurityContextHolder，
 * 后续 Controller 可通过 SecurityContext 拿到当前用户身份（作为 Agent 的 userId 与租户隔离维度）。
 * <p>
 * 注意：本类不加 {@code @Component}——WebFlux 会把 @Component WebFilter 自动注册为全局过滤器，
 * 与 SecurityConfig 的 {@code addFilterAt} 叠加会导致每请求重复解析两次 token；
 * 故仅由 {@code SecurityConfig} 声明为 Bean 并挂进安全链。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter implements WebFilter {

    /** Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final OnlineUserTracker onlineUserTracker;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // traceId 由 TraceFilter 写入上下文：桥接到 MDC，本过滤器同步段（含 token 解析）日志即带链路 ID
        return Mono.deferContextual(ctxView -> {
            ReactiveSupport.putTrace(ctxView.getOrDefault(TraceFilter.CONTEXT_KEY, null));
            try {
                // 客户端 IP 由 ClientIpFilter（最高优先级）写入上下文，供在线状态采集记录
                String clientIp = ctxView.getOrDefault(ClientIpFilter.CONTEXT_KEY, null);
                return doFilter(exchange, chain, clientIp);
            } finally {
                // 同步段结束即清理（后续异步段由 ReactiveSupport/匿名接口自行桥接），避免事件循环线程 MDC 串号
                MDC.remove(TraceFilter.MDC_KEY);
            }
        });
    }

    /** 实际的 token 解析与认证写入逻辑（执行期间当前线程 MDC 已带 traceId） */
    private Mono<Void> doFilter(ServerWebExchange exchange, WebFilterChain chain, String clientIp) {
        String token = resolveToken(exchange.getRequest());
        if (!StringUtils.hasText(token)) {
            // 未携带 token：放行给后续链路，由 Security 规则决定是否拒绝
            return chain.filter(exchange);
        }
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            // token 无效 / 过期：视为匿名请求，受保护接口将返回 401
            return chain.filter(exchange);
        }
        String username = claims.getSubject();
        List<String> roleKeys = extractRoleKeys(claims);
        Long tenantId = claims.get(JwtUtil.CLAIM_TENANT, Long.class);
        // 旧版 token 无 userId 声明，get 返回 null 不影响认证，仅审计 ID 填充缺失；
        // 旧版 token 的 userId 为 Long（数字），新版为 String（租户编码_序号），统一转 String 兼容
        Object userIdObj = claims.get(JwtUtil.CLAIM_USER_ID);
        String userId = userIdObj != null ? userIdObj.toString() : null;
        LoginUser loginUser = new LoginUser(userId, username, tenantId, roleKeys);
        // 在线状态采集：认证通过即视为活跃（内存写入无阻塞，不影响请求链路）
        onlineUserTracker.touch(userId, username, tenantId, clientIp);
        // 角色键映射为 ROLE_ 前缀权限，供 hasRole / @PreAuthorize 使用
        List<SimpleGrantedAuthority> authorities = roleKeys.stream()
                .map(roleKey -> new SimpleGrantedAuthority(RoleConstants.AUTHORITY_PREFIX + roleKey.toUpperCase()))
                .collect(Collectors.toList());
        // 权限点（如 system:user:add）原样授予，驱动方法级按钮鉴权 hasAuthority；
        // 旧版 token 无此声明时仅角色粗粒度鉴权可用，重新登录后恢复
        extractPermissions(claims).forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));
        var authentication = new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    /** 从 Claims 中提取角色键列表（兼容缺失场景，返回空列表） */
    private List<String> extractRoleKeys(Claims claims) {
        Object roles = claims.get(JwtUtil.CLAIM_ROLES);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /** 从 Claims 中提取权限点列表（旧版 token 无此声明，返回空列表） */
    private List<String> extractPermissions(Claims claims) {
        Object permissions = claims.get(JwtUtil.CLAIM_PERMISSIONS);
        if (permissions instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /** 提取 Authorization 头中的 Bearer token */
    private String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
