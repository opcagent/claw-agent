package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户ID */
    private Long tenantId;

    /** 父部门ID（根为 0） */
    private Long parentId;

    /** 父链（逗号分隔，含根 0） */
    private String ancestors;

    /** 部门名称 */
    private String deptName;

    /** 显示顺序 */
    private Integer orderNum;

    /** 负责人用户名 */
    private String leader;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

}
