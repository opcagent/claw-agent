package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属租户ID（关联 sys_tenant.id，多租户隔离的组织维度） */
    private Long tenantId;

    /** 所属部门ID（关联 sys_dept.id） */
    private Long deptId;

    /** 登录用户名（唯一），同时作为 Agent 的 userId */
    private String username;

    /** BCrypt 加密后的密码 */
    private String password;

    /** 昵称（展示用） */
    private String nickname;

    /** 手机号码 */
    private String phone;

    /** 电子邮箱 */
    private String email;

    /** 性别：0 未知 / 1 男 / 2 女 */
    private Integer gender;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 是否启用 */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
