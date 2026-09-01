package com.claw.agent.common;

/**
 * 角色与权限标识常量。
 * <p>
 * 收拢散落在各控制器/过滤器中的角色键魔法值：
 * 角色键写入 JWT 并在 {@code JwtAuthFilter} 中映射为 {@code ROLE_<KEY>} 权限。
 */
public final class RoleConstants {

    /** 平台管理员角色键（拥有全部权限，鉴权短路） */
    public static final String ROLE_ADMIN = "admin";

    /** 租户管理员角色键（管理本租户用户/角色/部门/菜单关联） */
    public static final String ROLE_TENANT_ADMIN = "tenant_admin";

    /** 普通用户角色键（自助注册默认授予） */
    public static final String ROLE_COMMON = "common";

    /** Spring Security 权限前缀（角色键映射为 ROLE_ + 大写键） */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    /** admin 角色的通配权限标识（若依约定） */
    public static final String ALL_PERMISSIONS = "*:*:*";

    private RoleConstants() {
    }
}
