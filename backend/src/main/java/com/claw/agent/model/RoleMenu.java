package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-菜单关联实体（表 sys_role_menu，多对多）。
 * <p>
 * 权限点授权关系：角色拥有哪些菜单/按钮权限（perms）。
 * 单列自增主键 + (role_id, menu_id) 唯一键（MyBatis Plus 要求实体有主键）。
 */
@Data
@TableName("sys_role_menu")
public class RoleMenu {

    /** 主键ID（自增，insert 时留空由数据库生成） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID */
    private Long roleId;

    /** 菜单/按钮权限ID */
    private Long menuId;

    /** 创建时间（库默认值填充，业务不传） */
    private LocalDateTime createTime;
}
