package com.claw.agent.config.security;

import com.claw.agent.common.Result;
import com.claw.agent.common.ResultCode;
import com.claw.agent.security.JwtAuthFilter;
import com.claw.agent.security.JwtUtil;
import com.claw.agent.service.OnlineUserTracker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security（WebFlux 响应式）安全配置。
 * <p>
 * 认证链路：JwtAuthFilter 解析 Bearer token 写入 ReactiveSecurityContextHolder；
 * 无 token / token 非法时按匿名处理，受保护路径统一返回 JSON 格式的 401。
 * <p>
 * 鉴权分级：登录/注册匿名放行；/api/admin/** 需租户管理员及以上（tenant_admin/admin）；
 * 其余 /api/** 仅需登录；作用域与记录归属级的细粒度鉴权由控制器 @PreAuthorize 与
 * 方法内校验承担（@EnableReactiveMethodSecurity 已启用）。
 * <p>
 * 前后端分离：后端只提供 /api/** 接口，静态页面由独立的 Next.js 前端工程承载，
 * 不再放行任何静态资源路径。
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final OnlineUserTracker onlineUserTracker;

    /** 密码编码器：BCrypt（与若依 / Flyway 初始数据的 hash 一致） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT 认证过滤器：仅在此声明为 Bean 并挂进安全链。
     * <p>
     * JwtAuthFilter 不带 @Component：否则 WebFlux 会把它自动注册为全局过滤器，
     * 与本处 {@code addFilterAt} 叠加，每请求重复解析两次 token。
     */
    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtUtil, onlineUserTracker);
    }

    /** 安全过滤链 */
    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                // 无状态 API：禁用 CSRF 与 session
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 跨域：使用容器内 CorsConfigurationSource Bean（前后端分离部署）
                .cors(Customizer.withDefaults())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // 未认证统一返回 JSON 401（而非跳转登录页）
                .exceptionHandling(spec -> spec.authenticationEntryPoint((exchange, ex) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    byte[] body = writeResult(ResultCode.UNAUTHORIZED);
                    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
                    return exchange.getResponse().writeWith(Mono.just(buffer));
                }))
                .authorizeExchange(exchange -> exchange
                        // 放行：登录 + 版本信息（登录页未登录也需展示品牌名）+ 渠道 Webhook（外部平台回调，无 JWT）
                        .pathMatchers("/api/auth/login", "/api/config/versionInfo", "/api/webhook/**").permitAll()
                        // 放行：OpenAPI 文档 + Swagger UI（仅开发环境开启，生产环境应关闭 springdoc）
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 管理端点：租户管理员及以上（角色键 tenant_admin / admin → ROLE_TENANT_ADMIN / ROLE_ADMIN）
                        .pathMatchers("/api/admin/**").hasAnyRole("ADMIN", "TENANT_ADMIN")
                        // 其余业务接口必须登录
                        .anyExchange().authenticated())
                // JWT 过滤器置于认证链之前（单例挂链，见 jwtAuthFilter() Bean 声明）
                .addFilterAt(jwtAuthFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /** 把统一返回体序列化为 JSON 字节 */
    private byte[] writeResult(ResultCode resultCode) {
        try {
            return objectMapper.writeValueAsBytes(Result.fail(resultCode));
        } catch (JsonProcessingException e) {
            // 兜底：序列化失败时返回最小 JSON
            return "{\"code\":401,\"message\":\"未登录或登录已过期\"}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
