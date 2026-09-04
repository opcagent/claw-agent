package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户实体（表 sys_tenant）。
 * <p>
 * 多租户架构的组织维度：用户归属租户，配置按 GLOBAL &gt; TENANT &gt; USER 三级解析。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("sys_tenant")
public class Tenant extends BaseEntity {

    /** 租户ID（主键） */
    @Schema(description = "租户ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户编码（唯一，英文标识） */
    @Schema(description = "租户编码")
    private String tenantCode;

    /** 租户名称 */
    @Schema(description = "租户名称")
    private String tenantName;

    /** 状态：1 启用 / 0 禁用 */
    @Schema(description = "状态：1启用/0禁用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

}
