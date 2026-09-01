package com.claw.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增租户请求（携带初始管理员信息）。
 * <p>
 * 平台管理员创建新租户时，可同时指定该租户的初始管理员账号。
 * 系统会自动创建用户并授予 tenant_admin 角色。
 */
@Data
public class TenantCreateWithAdminRequest {

    // ==================== 租户基本信息 ====================

    /** 租户编码（英文标识，如 acme） */
    @NotBlank(message = "租户编码不能为空")
    private String tenantCode;

    /** 租户名称 */
    @NotBlank(message = "租户名称不能为空")
    private String tenantName;

    /** 状态：0-禁用 / 1-启用 */
    private Integer status = 1;

    /** 备注 */
    private String remark;

    // ==================== 初始管理员信息（可选） ====================

    /** 管理员用户名（留空则不创建管理员） */
    private String adminUsername;

    /** 管理员密码（必填，如果提供了 adminUsername） */
    private String adminPassword;

    /** 管理员昵称 */
    private String adminNickname;

    /** 管理员手机号 */
    private String adminPhone;

    /** 管理员邮箱 */
    private String adminEmail;

    /** 管理员性别：0-未知 / 1-男 / 2-女 */
    private Integer adminGender = 0;
}
