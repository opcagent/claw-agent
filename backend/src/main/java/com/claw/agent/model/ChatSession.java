package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天会话元数据实体（对应数据库表 chat_session）。
 * <p>
 * 只存会话的「索引信息」（标题、时间），对话内容本身由
 * AgentScope 的会话日志（workspace/agents/&lt;agentId&gt;/sessions/）维护，
 * 两者职责分离、互不重复。
 */
@Data
@EqualsAndHashCode(callSuper=false)
@TableName("chat_session")
public class ChatSession extends BaseEntity {

    /** 主键（数据库自增） */
    @Schema(description = "会话ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户ID（按租户审计） */
    @Schema(description = "所属租户ID")
    private Long tenantId;

    /** AgentScope sessionId */
    @Schema(description = "AgentScope会话ID")
    private String sessionId;

    /** 所属用户（对应 sys_user.username） */
    @Schema(description = "所属用户")
    private String username;

    /** 会话标题（首条消息摘要，前端展示用） */
    @Schema(description = "会话标题")
    private String title;

    /** 会话摘要（跨会话记忆用，对话结束后自动生成，注入新会话系统提示词） */
    @Schema(description = "会话摘要")
    private String summary;

    /** 是否归档：0 活跃 1 已归档（归档后从主列表隐藏，可在「归档」Tab 查看/恢复/删除） */
    @Schema(description = "归档状态：0活跃/1已归档")
    private Integer archived;
}
