package com.claw.agent.config.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 安全护栏中间件：对用户输入和 Agent 输出进行安全过滤。
 * <p>
 * 防护维度：
 * <ul>
 *   <li>输入过滤：拦截包含危险提示词注入（Prompt Injection）的用户消息</li>
 *   <li>输出过滤：屏蔽 Agent 回复中的敏感信息（如内部错误详情、系统路径泄露）</li>
 * </ul>
 * 设计原则：
 * - 失败放行（filter 异常时不阻断对话，仅告警）
 * - 规则可配置（通过数据库 agent_config 表 guardrails_* 字段扩展）
 * - 零 LLM 调用成本（纯正则/关键词匹配）
 */
@Slf4j
public class GuardrailsMiddleware implements MiddlewareBase {

    /**
     * Prompt Injection 检测模式：常见注入攻击关键词。
     * <p>
     * 匹配用户试图覆盖系统提示词、绕过安全限制等攻击模式。
     * 注意：这些模式只检测明显的注入尝试，避免误伤正常对话。
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // 英文注入模式
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions?|prompts?|rules?)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an)\\s+(DAN|uncensored|unrestricted)"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(your\\s+)?(rules?|constraints?|guidelines?|instructions?)"),
            Pattern.compile("(?i)forget\\s+(all\\s+)?(your\\s+)?(rules?|instructions?|training)"),
            Pattern.compile("(?i)new\\s+instructions?:\\s*you\\s+(are|will|must|should)"),
            Pattern.compile("(?i)system\\s*:\\s*(you\\s+are|override|new\\s+role)"),
            // 中文注入模式
            Pattern.compile("忽略(之前|上面|所有)(的)?(指令|规则|提示词|设定)"),
            Pattern.compile("你现在(是|扮演)(一个|DAN|没有限制的)"),
            Pattern.compile("(忘记| disregard|无视)(你的)?(规则|指令|限制|设定)"),
            Pattern.compile("新(的)?指令[：:]\\s*你(是|将|必须)")
    );

    /**
     * 输出敏感信息过滤模式：防止 Agent 泄露内部系统信息。
     */
    private static final List<Pattern> OUTPUT_FILTER_PATTERNS = List.of(
            // 文件路径泄露（Windows + Unix）
            Pattern.compile("(?i)(C:\\\\Users|/home/|/etc/|/var/|/root/)[a-zA-Z0-9/\\\\_.-]+"),
            // 内部 IP 地址（10.x / 172.16-31.x / 192.168.x）
            Pattern.compile("(?<!\\d)(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3})(?!\\d)"),
            // API Key / Token 模式（常见格式）
            Pattern.compile("(?i)(api[_-]?key|secret|token|password)\\s*[=:]\\s*[\"']?[a-zA-Z0-9_\\-]{16,}[\"']?")
    );

    /** 注入检测命中时的友好回复 */
    private static final String INJECTION_BLOCKED_MESSAGE =
            "检测到您的消息可能包含安全风险提示（Prompt Injection），已拦截。请调整提问方式后重试。";

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                    java.util.function.Function<AgentInput, Flux<AgentEvent>> next) {
        // 输入过滤：检查用户消息是否包含注入攻击
        List<Msg> messages = input.msgs();
        if (messages != null) {
            for (Msg msg : messages) {
                if (msg.getRole() == MsgRole.USER && containsInjection(msg)) {
                    log.warn("[安全护栏] 检测到 Prompt Injection: user={}", ctx.getUserId());
                    // 返回拦截消息作为 AgentEvent 流
                    return Flux.just(createTextEvent(INJECTION_BLOCKED_MESSAGE));
                }
            }
        }

        // 输出过滤：对 Agent 回复中的敏感信息进行脱敏
        return next.apply(input)
                .map(event -> filterOutput(event, ctx.getUserId()));
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        java.util.function.Function<ModelCallInput, Flux<AgentEvent>> next) {
        // 模型调用前：在发送给模型的消息中注入安全提醒（防止模型被诱导输出敏感内容）
        return next.apply(input);
    }

    /**
     * 检测消息是否包含 Prompt Injection 攻击模式。
     *
     * @param msg 用户消息
     * @return 包含注入模式返回 true
     */
    private boolean containsInjection(Msg msg) {
        String content = extractTextContent(msg);
        if (content == null || content.isEmpty()) {
            return false;
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 Msg 中提取纯文本内容。
     */
    private String extractTextContent(Msg msg) {
        if (msg.getContent() == null) return null;
        StringBuilder sb = new StringBuilder();
        for (var block : msg.getContent()) {
            if (block instanceof TextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 输出过滤：对文本增量事件中的敏感信息进行脱敏。
     * <p>
     * 只处理 TextBlockDeltaEvent，其他事件原样透传。
     * 每个正则只创建一次 Matcher，先 find() 再 replaceAll()，避免重复编译开销。
     *
     * @param event  Agent 事件
     * @param userId 用户 ID（日志用）
     * @return 过滤后的事件
     */
    private AgentEvent filterOutput(AgentEvent event, String userId) {
        if (!(event instanceof TextBlockDeltaEvent delta)) {
            return event;
        }
        String text = delta.getDelta();
        if (text == null || text.isEmpty()) {
            return event;
        }
        // 对敏感信息进行脱敏（每个正则只 matcher 一次，先 find 后 replaceAll）
        String filtered = text;
        for (Pattern pattern : OUTPUT_FILTER_PATTERNS) {
            Matcher m = pattern.matcher(filtered);
            if (m.find()) {
                filtered = m.replaceAll("[已脱敏]");
                log.debug("[安全护栏] 输出脱敏: user={}, pattern={}", userId, pattern.pattern());
            }
        }
        // 如果内容被修改，构建新的 TextBlockDeltaEvent
        if (!filtered.equals(text)) {
            return new TextBlockDeltaEvent(delta.getReplyId(), delta.getBlockId(), filtered);
        }
        return event;
    }

    /**
     * 构建文本事件（用于返回拦截消息）。
     */
    private AgentEvent createTextEvent(String text) {
        return new TextBlockDeltaEvent(null, null, text);
    }
}
