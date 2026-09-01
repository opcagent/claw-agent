package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单/权限实体（表 sys_menu，平台级，租户共享）。
 * <p>
 * 类型：M 目录 / C 菜单 / F 按钮；perms 为权限标识（如 system:user:add），
 * 登录后聚合用户所有角色的 perms 下发前端，配合 @PreAuthorize 鉴权。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("sys_menu")
public class Menu extends BaseEntity {

    /** 菜单类型：目录 */
    public static final String TYPE_DIR = "M";
    /** 菜单类型：菜单 */
    public static final String TYPE_MENU = "C";
    /** 菜单类型：按钮（权限点） */
    public static final String TYPE_BUTTON = "F";

    /** 菜单ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父菜单ID（根为 0） */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 类型：M 目录 / C 菜单 / F 按钮 */
    private String menuType;

    /** 显示顺序 */
    private Integer orderNum;

    /** 路由地址 / 组件路径 */
    private String path;

    /** 菜单图标 */
    private String icon;

    /** 权限标识（如 system:user:add） */
    private String perms;

    /** 是否显示：1 显示 / 0 隐藏 */
    private Integer visible;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;
}
