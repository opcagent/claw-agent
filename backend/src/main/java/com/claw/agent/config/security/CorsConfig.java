package com.claw.agent.config.security;

import com.claw.agent.config.infra.ClawProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 跨域配置（前后端分离部署）。
 * <p>
 * 前端（Next.js）独立端口/域名部署时，浏览器跨源请求需后端放行；
 * 开发期前端通过 Next.js rewrites 同源代理可绕开，但直连模式与生产
 * 独立部署（Nginx 分域）仍依赖本配置。
 * <p>
 * 安全约束：携带凭证（Authorization 头 + 预检）场景下不允许使用「*」通配来源，
 * 必须用 allowedOriginPatterns 精确枚举（见 {@link ClawProperties.Cors}）。
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final ClawProperties properties;

    /**
     * 响应式跨域配置源：对 /api/** 全量放行方法与请求头。
     * <p>
     * Spring Security WebFlux 检测到该 Bean 后，预检（OPTIONS）请求
     * 会在安全链内被正确处理，不会因未认证被 401 拦截。
     *
     * @return 跨域配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 精确来源列表（生产环境通过 claw.cors.allowed-origin-patterns 收敛为具体域名）
        config.setAllowedOriginPatterns(properties.getCors().getAllowedOriginPatterns());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(properties.getCors().getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
