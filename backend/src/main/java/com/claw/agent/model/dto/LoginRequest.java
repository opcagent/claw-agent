package com.claw.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求 DTO。
 */
@Data
public class LoginRequest {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（明文，服务端 BCrypt 校验） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度 6-64 位")
    private String password;
}
