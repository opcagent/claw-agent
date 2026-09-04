package com.claw.agent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组织简要信息（登录时返回，供用户选择登录组织）。
 * <p>
 * 包含组织 ID、编码、名称以及用户在该组织内的角色键列表，
 * 前端据此渲染组织选择界面。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantBrief {

    /** 租户ID */
    private Long tenantId;

    /** 租户编码 */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 用户在该组织内的角色键列表（如 tenant_admin / common） */
    private List<String> roleKeys;

    /** 是否为默认登录组织 */
    private Boolean isDefault;
}
