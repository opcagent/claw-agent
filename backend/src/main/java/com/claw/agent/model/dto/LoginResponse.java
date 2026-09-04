package com.claw.agent.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 登录响应 DTO：token + 用户基础信息 + 角色键 + 权限标识列表。
 * <p>
 * 权限列表（如 system:user:add）下发前端，用于菜单/按钮级显隐控制（若依模式）。
 * 多组织场景下登录时自动选择 is_default=1 的默认组织，无需前端干预。
 */
@Data
@Builder
public class LoginResponse {

    /** JWT token */
    private String token;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 所属租户ID（当前活跃组织） */
    private Long tenantId;

    /** 所属租户名称（展示用，租户不存在时为空） */
    private String tenantName;

    /** 角色键列表（如 admin / common） */
    private List<String> roles;

    /** 权限标识列表（如 chat:session:list；admin 角色返回 *:*:*） */
    private List<String> permissions;
}
