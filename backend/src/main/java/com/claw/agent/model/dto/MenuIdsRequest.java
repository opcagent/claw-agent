package com.claw.agent.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 菜单授权保存请求（角色管理：全量替换角色菜单；菜单管理：全量替换菜单关联角色）。
 */
@Data
public class MenuIdsRequest {

    /** 授权的菜单/按钮ID列表 */
    private List<Long> menuIds;

    /** 关联的角色ID列表（菜单关联角色方向使用） */
    private List<Long> roleIds;
}
