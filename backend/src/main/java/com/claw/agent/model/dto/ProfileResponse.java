package com.claw.agent.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人信息详情响应（个人中心展示用）。
 * <p>
 * 聚合本人基础资料（账号/昵称/租户/角色/创建时间）与最近一次成功登录信息，
 * 数据来源均为服务端（JWT 定位本人），不信任前端传参。
 */
@Data
@Builder
public class ProfileResponse {

    /** 登录用户名 */
    private String username;

    /** 昵称（展示用） */
    private String nickname;

    /** 手机号码 */
    private String phone;

    /** 电子邮箱 */
    private String email;

    /** 性别：0 未知 / 1 男 / 2 女 */
    private Integer gender;

    /** 所属租户ID */
    private Long tenantId;

    /** 所属租户名称（展示用，租户不存在时为空） */
    private String tenantName;

    /** 角色键列表（如 admin / tenant_admin / common） */
    private List<String> roleKeys;

    /** 角色名称列表（与 roleKeys 同序，展示用） */
    private List<String> roleNames;

    /** 账号创建时间 */
    private LocalDateTime createTime;

    /** 最近一次成功登录时间（无登录记录时为空） */
    private LocalDateTime lastLoginTime;

    /** 最近一次成功登录 IP */
    private String lastLoginIp;
}
