package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户功能模块配置实体（表 tenant_feature）。
 * <p>
 * 平台管理员可为每个租户配置可用的功能模块（菜单），
 * 租户管理员只能使用平台管理员配置的功能模块。
 * 未配置的租户默认拥有全部功能（向后兼容）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tenant_feature")
public class TenantFeature extends BaseEntity {

    /** 主键 */
    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（关联 sys_tenant.id） */
    @Schema(description = "租户ID")
    private Long tenantId;

    /** 菜单/功能ID（关联 sys_menu.id） */
    @Schema(description = "菜单ID")
    private Long menuId;

    /** 是否启用：1 启用 / 0 禁用 */
    @Schema(description = "启用状态：1启用/0禁用")
    private Integer enabled;
}
