package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-部门关联实体（表 sys_role_dept，多对多）。
 * <p>
 * 仅在角色 data_scope=2（自定义数据权限）时生效，
 * 定义该角色可见的部门集合。
 * 单列自增主键 + (role_id, dept_id) 唯一键（MyBatis Plus 要求实体有主键）。
 */
@Data
@TableName("sys_role_dept")
public class RoleDept {

    /** 主键ID（自增，insert 时留空由数据库生成） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID */
    private Long roleId;

    /** 部门ID */
    private Long deptId;

    /** 创建时间（库默认值填充，业务不传） */
    private LocalDateTime createTime;
}
