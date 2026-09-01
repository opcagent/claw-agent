package com.claw.agent.model.dto;

import lombok.Data;

/**
 * 本人资料自助更新请求（个人中心）。
 * <p>
 * 仅允许修改昵称与联系方式；用户名/租户/角色等身份字段不可自助变更。
 * 操作对象由 JWT 定位，不信任前端传参。
 */
@Data
public class ProfileUpdateRequest {

    /** 昵称（展示用） */
    private String nickname;

    /** 手机号码（可选，格式服务端校验） */
    private String phone;

    /** 电子邮箱（可选，格式服务端校验） */
    private String email;

    /** 性别：0 未知 / 1 男 / 2 女 */
    private Integer gender;
}
