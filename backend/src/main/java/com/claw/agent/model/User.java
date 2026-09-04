package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体（对应数据库表 sys_user）。
 * <p>
 * id 格式为 {租户编码}_{自增序号}，作为 AgentScope 的 userId 与所有作用域表的归属键，
 * 不可变且含业务语义，避免 username 变更导致配置孤立。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("sys_user")
public class User extends BaseEntity {

    /** 主键（格式：租户编码_自增序号，由 UserServiceImpl 生成） */
    @Schema(description = "用户ID")
    @TableId(type = IdType.INPUT)
    private String id;

    /** 登录用户名（唯一），同时作为 Agent 的 userId */
    @Schema(description = "用户名")
    private String username;

    /** BCrypt 加密后的密码 */
    @Schema(description = "密码", accessMode = Schema.AccessMode.READ_ONLY)
    private String password;

    /** 昵称（展示用） */
    @Schema(description = "昵称")
    private String nickname;

    /** 手机号码 */
    @Schema(description = "手机号")
    private String phone;

    /** 电子邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 性别：0 未知 / 1 男 / 2 女 */
    @Schema(description = "性别：0未知/1男/2女")
    private Integer gender;

    /** 状态：1 启用 / 0 禁用 */
    @Schema(description = "状态：1启用/0禁用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 是否启用 */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
