package com.claw.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增用户请求（用户管理）。
 * <p>
 * 必填字段加 Bean Validation 约束，配合控制器 {@code @Valid} 把非法入参
 * 在入口层转为 400，避免空值穿透到业务层抛出 500。
 */
@Data
public class UserCreateRequest {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 30, message = "用户名长度需在 2-30 个字符之间")
    private String username;

    /** 密码（明文传输，服务端加密） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 个字符之间")
    private String password;

    /** 昵称 */
    private String nickname;

    /** 手机号码（可选，格式服务端校验） */
    private String phone;

    /** 电子邮箱（可选，格式服务端校验） */
    private String email;

    /** 性别：0 未知 / 1 男 / 2 女 */
    private Integer gender;

    /** 部门ID */
    private Long deptId;

    /** 备注 */
    private String remark;
}
