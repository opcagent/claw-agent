package com.claw.agent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 租户功能配置请求 DTO。
 */
@Data
@Schema(description = "租户功能配置请求")
public class TenantFeatureRequest {

    /** 启用的菜单ID列表 */
    @Schema(description = "启用的菜单ID列表")
    private List<Long> menuIds;
}
