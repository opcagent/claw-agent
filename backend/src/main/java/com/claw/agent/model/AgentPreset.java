package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预设 Agent 模板实体（表 agent_preset，三级作用域）。
 * <p>
 * scope：PLATFORM 平台内置 / TENANT 租户自定义 / USER 用户个人；
 * 对话时选择模板，其 sys_prompt（Markdown 人格模板）注入该会话系统提示词。
 * 可见性规则：USER 模板仅本人可见，TENANT 模板本租户可见，PLATFORM 全员可见。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("agent_preset")
public class AgentPreset extends BaseEntity {

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

    /** 模板编码（同作用域内唯一，如 researcher） */
    @Schema(description = "模板编码")
    private String agentCode;

    /** 模板名称（如 研究分析助手） */
    @Schema(description = "模板名称")
    private String agentName;

    /** 图标标识 */
    @Schema(description = "图标")
    private String icon;

    /** 一句话简介（卡片展示） */
    @Schema(description = "简介")
    private String description;

    /** 人格模板（Markdown，注入系统提示词） */
    @Schema(description = "系统提示词")
    private String sysPrompt;

    /** 显示顺序 */
    @Schema(description = "显示顺序")
    private Integer orderNum;

    /** 是否启用：1 启用 / 0 禁用 */
    @Schema(description = "启用状态：1启用/0禁用")
    private Integer enabled;

    /** 是否发布到市场：0 否 / 1 是 */
    @Schema(description = "市场发布：0否/1是")
    private Integer published;

    /** 发布名称（市场展示用，可与 agentName 不同） */
    @Schema(description = "发布名称")
    private String publishName;

    /** 发布描述（市场展示用） */
    @Schema(description = "发布描述")
    private String publishDesc;

    /** 市场使用次数 */
    @Schema(description = "使用次数")
    private Integer useCount;

    /** 作者名称（市场展示用，发布时从归属用户回填） */
    @Schema(description = "作者")
    private String authorName;
}
