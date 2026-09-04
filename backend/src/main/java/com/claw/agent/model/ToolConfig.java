package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具开关配置实体（表 tool_config，三级作用域）。
 * <p>
 * 每行表示「某作用域下某工具被显式设置为启用/禁用」；
 * 无记录视为默认启用。解析时按 USER &gt; TENANT &gt; GLOBAL 就近取第一个命中值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tool_config")
public class ToolConfig extends BaseEntity {

    /** 主键 */
    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作用域：GLOBAL / TENANT / USER */
    @Schema(description = "作用域")
    private String scope;

    /** 租户ID（GLOBAL 为 0） */
    @Schema(description = "租户ID")
    private Long tenantId;

    /** 归属用户ID（USER 作用域为 sys_user.id，非 USER 为 null） */
    @Schema(description = "归属用户ID")
    private String ownerId;

    /** 工具键：内置/自定义工具的固定标识 */
    @Schema(description = "工具键")
    private String toolKey;

    /** 是否启用：1 启用 / 0 禁用 */
    @Schema(description = "启用状态：1启用/0禁用")
    private Integer enabled;
}
