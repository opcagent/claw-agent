package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体（表 sys_role）。
 * <p>
 * 数据权限五档（若依约定，见 data_scope）：
 * 1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("sys_role")
public class Role extends BaseEntity {

    /** 数据权限：全部数据 */
    public static final int DATA_SCOPE_ALL = 1;
    /** 数据权限：自定义（角色-部门关联） */
    public static final int DATA_SCOPE_CUSTOM = 2;
    /** 数据权限：本部门 */
    public static final int DATA_SCOPE_DEPT = 3;
    /** 数据权限：本部门及以下 */
    public static final int DATA_SCOPE_DEPT_AND_CHILD = 4;
    /** 数据权限：仅本人 */
    public static final int DATA_SCOPE_SELF = 5;

    /** 角色ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户ID（平台超管角色租户为 0） */
    private Long tenantId;

    /** 角色名称 */
    private String roleName;

    /** 角色权限字符串（如 admin / common，写入 JWT 与鉴权使用） */
    private String roleKey;

    /** 显示顺序 */
    private Integer roleSort;

    /** 数据权限档位（1-5） */
    private Integer dataScope;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

}
