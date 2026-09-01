package com.claw.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.mapper.ChatMessageMapper;
import com.claw.agent.mapper.ChatSessionMapper;
import com.claw.agent.model.ChatMessage;
import com.claw.agent.model.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 会话摘要服务：自动生成与维护跨会话记忆。
 * <p>
 * 采用抽取式摘要（extractive summary）：取首条用户消息 + 首条助手回复的前 N 字，
 * 零 LLM 调用成本，可靠性高；后续可升级为 LLM 摘要。
 * <p>
 * 注入时机：{@code AgentRegistry.build()} 构建 Agent 时取最近 5 个会话摘要拼入系统提示词，
 * 让 Agent 知道用户最近在做什么，实现跨会话上下文延续。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSummaryService {

    /** 摘要最大字符数（防止过长占用上下文） */
    private static final int SUMMARY_MAX_LENGTH = 200;

    /** 注入系统提示词时取最近几个会话的摘要 */
    private static final int INJECT_RECENT_COUNT = 5;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 异步生成/更新会话摘要：对话结束后由 AgentService 调用。
     * <p>
     * 在 boundedElastic 线程池异步执行，不阻塞对话主流程；
     * 失败仅告警，不影响用户体验。
     *
     * @param sessionId 会话ID
     * @param username  用户名
     */
    public void generateSummaryAsync(String sessionId, String username) {
        Mono.fromRunnable(() -> {
            try {
                doGenerate(sessionId, username);
            } catch (Exception e) {
                log.warn("会话摘要生成失败（不影响对话）: session={}, error={}", sessionId, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    /**
     * 查询用户最近 N 个会话的摘要（按活跃时间倒序），用于注入系统提示词。
     *
     * @param username 用户名
     * @return 摘要列表（不含 null，按时间倒序）
     */
    public List<String> getRecentSummaries(String username) {
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUsername, username)
                        .isNotNull(ChatSession::getSummary)
                        .ne(ChatSession::getSummary, "")
                        .orderByDesc(ChatSession::getUpdateTime)
                        .last("LIMIT " + INJECT_RECENT_COUNT));
        return sessions.stream()
                .map(ChatSession::getSummary)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 同步生成摘要（内部方法）：取首条用户消息 + 首条助手回复，截取前 N 字。
     */
    private void doGenerate(String sessionId, String username) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getUsername, username)
                        .eq(ChatMessage::getStatus, ChatMessage.STATUS_OK)
                        .orderByAsc(ChatMessage::getId)
                        .last("LIMIT 4"));
        if (messages.isEmpty()) {
            return;
        }
        // 拼接首条用户消息和首条助手回复
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            if (!StringUtils.hasText(msg.getContent())) continue;
            String content = msg.getContent().replaceAll("\\s+", " ").trim();
            if (ChatMessage.ROLE_USER.equals(msg.getRole())) {
                if (sb.length() > 0) continue; // 只取第一条用户消息
                sb.append("用户: ").append(content);
            } else if (ChatMessage.ROLE_ASSISTANT.equals(msg.getRole())) {
                if (sb.indexOf("助手: ") >= 0) continue; // 只取第一条助手回复
                sb.append(" | 助手: ").append(content);
            }
        }
        if (sb.length() == 0) return;

        String summary = sb.length() > SUMMARY_MAX_LENGTH
                ? sb.substring(0, SUMMARY_MAX_LENGTH) + "..."
                : sb.toString();

        // 更新会话摘要
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUsername, username)
                        .last("LIMIT 1"));
        if (session != null) {
            session.setSummary(summary);
            chatSessionMapper.updateById(session);
            log.debug("会话摘要已更新: session={}, summary={}", sessionId,
                    summary.length() > 50 ? summary.substring(0, 50) + "..." : summary);
        }
    }
}
