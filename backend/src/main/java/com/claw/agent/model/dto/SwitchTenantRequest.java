package com.claw.agent.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 切换组织请求 DTO。
 * <p>
 * 已登录用户切换到另一个已加入的组织，服务端校验归属关系后重新签发 JWT。
 */
@Data
public class SwitchTenantRequest {

    /** 目标租户ID */
    @NotNull(message = "请选择目标组织")
    private Long tenantId;
}
