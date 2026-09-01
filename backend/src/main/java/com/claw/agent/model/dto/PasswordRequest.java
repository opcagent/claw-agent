package com.claw.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求（用户管理）。
 */
@Data
public class PasswordRequest {

    /** 新密码（明文传输，服务端加密；约束避免空值穿透到 BCrypt 报 500） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 个字符之间")
    private String newPassword;
}
