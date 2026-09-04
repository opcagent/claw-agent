package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门实体（表 sys_dept）。
 * <p>
 * 租户内树形组织：ancestors 存父链（如 0,100,101），
 * 支撑"本部门及以下"数据权限的快速过滤（LIKE 'ancestors%'）。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("sys_dept")
public class Dept extends BaseEntity {

    /** 部门ID（主键） */
    @Schema(description = "部门ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户ID */
    @Schema(description = "所属租户ID")
    private Long tenantId;

    /** 父部门ID（根为 0） */
    @Schema(description = "父部门ID")
    private Long parentId;

    /** 父链（逗号分隔，含根 0） */
    @Schema(description = "父链")
    private String ancestors;

    /** 部门名称 */
    @Schema(description = "部门名称")
    private String deptName;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer orderNum;

    /** 负责人用户名 */
    @Schema(description = "负责人")
    private String leader;

    /** 状态：1 启用 / 0 禁用 */
    @Schema(description = "状态：1启用/0禁用")
    private Integer status;

}
