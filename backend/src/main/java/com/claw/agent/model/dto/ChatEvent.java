package com.claw.agent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天 SSE 事件 DTO（服务端把 AgentScope 事件转译为前端可渲染的轻量结构）。
 * <p>
 * type 取值：
 * <ul>
 *   <li>start —— 回复开始（携带 replyId）</li>
 *   <li>text —— 增量文本</li>
 *   <li>tool_start / tool_end —— 工具调用开始 / 结束（含状态）</li>
 *   <li>confirm_request —— HITL 待确认（携带待确认工具列表）</li>
 *   <li>subagent —— 子 Agent 暴露（可渲染为新会话入口）</li>
 *   <li>end —— 回复结束</li>
 *   <li>error —— 执行异常</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEvent {

    /** 事件类型（见类注释） */
    private String type;

    /** 会话ID（前端关联消息用） */
    private String sessionId;

    /** 回复消息ID */
    private String replyId;

    /** 增量文本内容 */
    private String delta;

    /** 工具调用ID */
    private String toolCallId;

    /** 工具名称 */
    private String toolName;

    /** 工具入参（JSON 字符串，confirm_request 时展示给用户审阅） */
    private String toolInput;

    /** 工具执行结果状态（tool_end 时） */
    private String state;

    /** 子 Agent 标识（subagent 事件） */
    private String subagentId;

    /** 子 Agent 可见标签 */
    private String label;

    /** 待确认工具列表（confirm_request 时） */
    private List<PendingToolCall> pendingToolCalls;

    /** 错误信息（error 时） */
    private String message;

    /** 待确认的工具调用（展示给用户审批） */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingToolCall {

        /** 工具调用ID */
        private String toolCallId;

        /** 工具名称 */
        private String toolName;

        /** 工具入参（JSON 字符串） */
        private String toolInput;
    }
}
