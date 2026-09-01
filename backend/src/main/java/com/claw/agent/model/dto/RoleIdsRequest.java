package com.claw.agent.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色分配保存请求（用户管理：全量替换用户角色）。
 */
@Data
public class RoleIdsRequest {

    /** 分配的角色ID列表（全量替换） */
    private List<Long> roleIds;
}
