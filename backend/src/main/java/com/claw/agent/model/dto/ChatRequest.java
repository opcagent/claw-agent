package com.claw.agent.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 聊天请求 DTO。
 * <p>
 * sessionId 为空表示新建会话；presetCode 选择预设人格模板；
 * pipelineCode 选择编排流水线（执行剧本随消息注入当轮上下文）；
 * attachments 为已上传文件的存储名（经 /api/upload 上传），图片类会转为多模态内容块。
 */
@Data
public class ChatRequest {

    /** AgentScope 会话ID（为空时服务端生成新会话） */
    private String sessionId;

    /** 用户消息文本 */
    private String content;

    /** 预设 Agent 模板编码（可选，如 researcher / coder） */
    private String presetCode;

    /** 编排流水线编码（可选，执行剧本随消息注入） */
    private String pipelineCode;

    /** 附件存储名列表（来自上传接口返回的 fileName） */
    private List<String> attachments;
}
