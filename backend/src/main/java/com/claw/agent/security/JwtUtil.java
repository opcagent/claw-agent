package com.claw.agent.security;

import com.claw.agent.config.infra.ClawProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具：签发与解析 token（jjwt 0.12.x API）。
 * <p>
 * 阿里规约：密钥与过期时间统一从配置读取，禁止硬编码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    /** subject 声明键 */
    public static final String CLAIM_USERNAME = "username";
    /** 角色声明键（值为 roleKey 列表，RBAC 多角色） */
    public static final String CLAIM_ROLES = "roles";
    /** 租户声明键（多租户隔离维度） */
    public static final String CLAIM_TENANT = "tenantId";
    /** 用户ID声明键（审计字段填充与数据关联查询用） */
    public static final String CLAIM_USER_ID = "userId";
    /** 权限点声明键（值为权限标识列表，如 system:user:add，驱动方法级按钮鉴权） */
    public static final String CLAIM_PERMISSIONS = "permissions";

    /** 配置中的开发默认密钥：生产环境必须用环境变量 CLAW_JWT_SECRET 覆盖，否则任何拿到源码的人都能伪造任意身份 token */
    private static final String INSECURE_DEFAULT_SECRET = "claw-agent-secret-key-please-change-in-production-2026";

    private final ClawProperties properties;

    /** 签名密钥（HS256 要求 >= 32 字节） */
    private SecretKey key;

    /**
     * 签发 token（RBAC 多角色 + 权限点版）。
     *
     * @param userId      用户ID（审计填充与数据关联查询用；旧版 token 无此声明，解析时允许缺失）
     * @param username    用户名（作为 subject）
     * @param tenantId    所属租户ID（多租户隔离维度）
     * @param roleKeys    角色键列表（如 admin / common，来自 sys_role.role_key）
     * @param permissions 权限点列表（如 system:user:add，来自角色授权菜单聚合；旧版 token 允许缺失）
     * @return JWT 字符串
     */
    public String generateToken(String userId, String username, Long tenantId,
                                List<String> roleKeys, List<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getJwt().getExpirationHours() * 3600_000L);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TENANT, tenantId)
                .claim(CLAIM_ROLES, roleKeys)
                .claim(CLAIM_PERMISSIONS, permissions)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析并校验 token。
     *
     * @param token JWT 字符串
     * @return Claims；token 非法 / 过期时返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从 token 中提取用户名，失败返回 null */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.getSubject();
    }

    /**
     * 启动期密钥安全检查：仍在使用随源码提交的默认密钥时醒目告警。
     * <p>
     * 不阻断启动（保障本地开发体验），但部署时必须经环境变量覆盖，
     * 否则 HS256 密钥泄露即等于任意身份可伪造（含平台超管）。
     */
    @PostConstruct
    public void warnIfDefaultSecret() {
        if (INSECURE_DEFAULT_SECRET.equals(properties.getJwt().getSecret())) {
            log.error("⚠ JWT 正在使用随源码提交的默认密钥，存在伪造任意身份 token 的风险！"
                    + "请通过环境变量 CLAW_JWT_SECRET 注入独立密钥后再对外提供服务。");
        }
    }

    /** 懒加载签名密钥 */
    private synchronized SecretKey getKey() {
        if (key == null) {
            key = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        }
        return key;
    }
}
