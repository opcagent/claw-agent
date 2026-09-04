package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编排流水线模板实体（表 agent_pipeline，三级作用域）。
 * <p>
 * 把多步骤任务固化为可复用执行剧本：steps 为 Markdown 步骤说明
 * （Step N + 动作 + 输出），运行时由主 Agent 依次执行或委派子 Agent；
 * exception_handling 定义异常分支策略。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("agent_pipeline")
public class AgentPipeline extends BaseEntity {

    /** 作用域：平台内置 */
    public static final String SCOPE_PLATFORM = "PLATFORM";
    /** 作用域：租户自定义 */
    public static final String SCOPE_TENANT = "TENANT";
    /** 作用域：用户个人 */
    public static final String SCOPE_USER = "USER";

    /** 主键 */
    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作用域：PLATFORM / TENANT / USER */
    @Schema(description = "作用域")
    private String scope;

    /** 租户ID（PLATFORM 为 0） */
    @Schema(description = "租户ID")
    private Long tenantId;

    /** 归属用户ID（USER 作用域为 sys_user.id，非 USER 为 null） */
    @Schema(description = "归属用户ID")
    private String ownerId;

    /** 流水线编码（同作用域内唯一） */
    @Schema(description = "流水线编码")
    private String pipelineCode;

    /** 流水线名称 */
    @Schema(description = "流水线名称")
    private String pipelineName;

    /** 流程描述 */
    @Schema(description = "流程描述")
    private String description;

    /** 执行步骤（Markdown：Step N + 动作 + 输出） */
    @Schema(description = "执行步骤")
    private String steps;

    /** 异常处理策略（Markdown） */
    @Schema(description = "异常处理策略")
    private String exceptionHandling;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer orderNum;

    /** 是否启用：1 启用 / 0 禁用 */
    @Schema(description = "启用状态：1启用/0禁用")
    private Integer enabled;
}
