package com.claw.agent.model.dto;

import lombok.Data;

/**
 * HITL 工具确认请求 DTO。
 * <p>
 * Agent 因权限规则暂停等待确认时，前端展示待确认工具列表，
 * 用户点击「允许 / 拒绝」后携带本对象调用确认接口恢复执行。
 */
@Data
public class ConfirmRequest {

    /** AgentScope 会话ID */
    private String sessionId;

    /** 是否批准执行（拒绝时 Agent 将跳过该工具调用） */
    private Boolean approved;
}
