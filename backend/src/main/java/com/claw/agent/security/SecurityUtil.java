package com.claw.agent.security;

import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * 当前登录用户工具：从响应式安全上下文提取 LoginUser。
 * <p>
 * JwtAuthFilter 已把 LoginUser 作为 Authentication.principal 写入上下文，
 * Controller 统一通过本工具获取（避免各控制器重复编写提取逻辑）。
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 获取当前登录用户；未认证时抛 401 业务异常（由全局异常处理器转 JSON）。
     *
     * @return LoginUser（用户名 / 租户 / 角色集）
     */
    public static Mono<LoginUser> currentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> {
                    Authentication auth = ctx.getAuthentication();
                    if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
                        throw new BizException(ResultCode.UNAUTHORIZED);
                    }
                    return loginUser;
                })
                .switchIfEmpty(Mono.error(new BizException(ResultCode.UNAUTHORIZED)));
    }

    /**
     * 同步获取当前用户ID (非响应式上下文)。
     * <p>
     * 用于 Tool 调用等场景,如果不在 HTTP 请求上下文中则返回 null。
     *
     * @return 用户ID,如果无法获取则返回 null
     */
    public static String getUserId() {
        try {
            Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
                return loginUser.getUserId();
            }
        } catch (Exception e) {
            // 忽略异常,返回 null
        }
        return null;
    }

    /**
     * 同步获取当前租户ID (非响应式上下文)。
     * <p>
     * 用于 Tool 调用等场景,如果不在 HTTP 请求上下文中则返回 null。
     *
     * @return 租户ID,如果无法获取则返回 null
     */
    public static Long getTenantId() {
        try {
            Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
                return loginUser.getTenantId();
            }
        } catch (Exception e) {
            // 忽略异常,返回 null
        }
        return null;
    }
}
