package com.claw.agent.security;

import com.claw.agent.common.RoleConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 登录用户上下文（作为 Authentication 的 principal 传递）。
 * <p>
 * 承载 JWT 解析出的身份信息：用户ID、用户名、租户、角色键集合，
 * Controller / Service 从 ReactiveSecurityContextHolder 取出后使用，
 * 是租户隔离与权限判断的统一入口。
 */
@Getter
@AllArgsConstructor
public class LoginUser {

    /** 用户ID（格式：租户编码_自增序号，旧版 token 无此声明时为 null） */
    private final String userId;

    /** 登录用户名（同时是 Agent 的 userId） */
    private final String username;

    /** 所属租户ID */
    private final Long tenantId;

    /** 角色键列表（如 admin / tenant_admin / common） */
    private final List<String> roleKeys;

    /** 是否平台管理员 */
    public boolean isAdmin() {
        return roleKeys != null && roleKeys.contains(RoleConstants.ROLE_ADMIN);
    }

    /** 是否租户管理员及以上（平台管理员自动满足） */
    public boolean isTenantAdmin() {
        return isAdmin() || (roleKeys != null && roleKeys.contains(RoleConstants.ROLE_TENANT_ADMIN));
    }
}
