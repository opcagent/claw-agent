package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天消息实体（对应数据库表 chat_message）。
 * <p>
 * 对话内容逐条入库（用户消息 + 助手回复），支持历史会话回看与审计追溯；
 * 会话元数据（标题/活跃时间）仍由 chat_session 维护，两者职责分离。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_message")
public class ChatMessage extends BaseEntity {

    /** 消息角色：用户 */
    public static final String ROLE_USER = "user";

    /** 消息角色：助手 */
    public static final String ROLE_ASSISTANT = "assistant";

    /** 状态：正常 */
    public static final int STATUS_OK = 1;

    /** 状态：失败（助手回复执行异常） */
    public static final int STATUS_FAIL = 0;

    /** 主键（数据库自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户ID（按租户审计） */
    private Long tenantId;

    /** AgentScope sessionId（关联 chat_session.session_id） */
    private String sessionId;

    /** 所属用户（对应 sys_user.username） */
    private String username;

    /** 消息角色（user / assistant） */
    private String role;

    /** 消息文本内容 */
    private String content;

    /** 附件文件名 JSON 数组（仅用户消息） */
    private String attachments;

    /** 状态：1 正常 / 0 失败 */
    private Integer status;
}
