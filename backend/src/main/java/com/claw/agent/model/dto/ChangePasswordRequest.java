package com.claw.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求（个人中心：本人修改自己的登录密码）。
 * 操作对象固定取自 JWT 登录态，不信任前端传入的用户标识（防越权改他人密码）。
 */
@Data
public class ChangePasswordRequest {

    /** 原密码（用于身份核验） */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码：6-64 位 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度须在 6-64 位之间")
    private String newPassword;
}
