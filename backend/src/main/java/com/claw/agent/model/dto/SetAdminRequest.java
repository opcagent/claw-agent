package com.claw.agent.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设置租户管理员请求（平台管理员专用）。
 */
@Data
public class SetAdminRequest {

    /** 要设置为管理员的用户ID */
    @NotNull(message = "用户ID不能为空")
    private String userId;
}
