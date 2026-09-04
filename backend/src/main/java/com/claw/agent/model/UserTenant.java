package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户-组织关联实体（表 sys_user_tenant，合并成员资格 + 角色分配）。
 * <p>
 * 每个 (user_id, tenant_id, role_id) 唯一，表达用户在某个组织内的特定角色。
 * 同一用户可在不同组织拥有不同角色、部门和职位。
 * {@code is_default} 标记用户的默认登录组织。
 * <p>
 * 平台管理员（admin）不属于任何组织，无本表记录。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user_tenant")
public class UserTenant extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（关联 sys_user.id） */
    private String userId;

    /** 租户ID（关联 sys_tenant.id） */
    private Long tenantId;

    /** 角色ID（关联 sys_role.id，该组织内的角色） */
    private Long roleId;

    /** 该组织内的部门ID（关联 sys_dept.id） */
    private Long deptId;

    /** 该组织内的职位 */
    private String position;

    /** 该组织内的状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 是否默认登录组织：1 是 / 0 否 */
    private Integer isDefault;
}
