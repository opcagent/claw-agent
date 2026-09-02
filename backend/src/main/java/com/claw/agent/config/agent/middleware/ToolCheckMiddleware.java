package com.claw.agent.config.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 工具输入安全检查中间件：在工具执行前检查输入参数是否包含危险内容。
 * <p>
 * 防护维度：
 * <ul>
 *   <li>危险路径：检测 .ssh/.env/.git 等敏感目录访问</li>
 *   <li>危险命令：检测 rm -rf、format、del 等破坏性命令</li>
 *   <li>敏感信息：检测 API Key、密码等敏感信息泄露</li>
 * </ul>
 * 设计原则：
 * - 失败放行（检查异常时不阻断工具执行，仅告警）
 * - 规则可配置（通过数据库 agent_config 表 tool_check_* 字段扩展）
 * - 零 LLM 调用成本（纯正则/关键词匹配）
 */
@Slf4j
public class ToolCheckMiddleware implements MiddlewareBase {

    /**
     * 危险路径检测模式：防止访问敏感系统目录。
     */
    private static final List<Pattern> DANGEROUS_PATH_PATTERNS = List.of(
            // 敏感目录
            Pattern.compile("(?i)(\\.ssh|\\.env|\\.git|\\.aws|\\.azure|\\.gcp)[/\\\\]"),
            // 系统配置文件
            Pattern.compile("(?i)(/etc/passwd|/etc/shadow|/etc/hosts|windows/system32)"),
            // 临时文件目录（可能被用于路径穿越）
            Pattern.compile("(?i)(/tmp/|/var/tmp/|C:\\\\Windows\\\\Temp\\\\).*\\.\\.(?!/)")
    );

    /**
     * 危险命令检测模式：防止执行破坏性操作。
     */
    private static final List<Pattern> DANGEROUS_COMMAND_PATTERNS = List.of(
            // 文件删除命令
            Pattern.compile("(?i)rm\\s+(-rf?|--recursive|--force)\\s+"),
            Pattern.compile("(?i)(del|remove-item)\\s+(-recurse|-force)\\s+"),
            // 磁盘格式化
            Pattern.compile("(?i)(format|mkfs|diskpart)\\s+"),
            // 系统关机/重启
            Pattern.compile("(?i)(shutdown|reboot|init\\s+0)\\s+"),
            // 权限提升
            Pattern.compile("(?i)(chmod\\s+777|chown\\s+root|sudo\\s+su)\\s+")
    );

    /**
     * 敏感信息检测模式：防止工具参数中包含敏感信息。
     */
    private static final List<Pattern> SENSITIVE_INFO_PATTERNS = List.of(
            // API Key / Token
            Pattern.compile("(?i)(api[_-]?key|secret|token)\\s*[=:]\\s*[\"']?[a-zA-Z0-9_\\-]{20,}[\"']?"),
            // 密码
            Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*[\"']?\\S+[\"']?"),
            // 数据库连接字符串
            Pattern.compile("(?i)(mysql|postgres|mongodb)://[^\\s]+:[^\\s]+@")
    );

    /** 工具输入安全检查命中时的友好回复 */
    private static final String TOOL_CHECK_BLOCKED_MESSAGE =
            "检测到工具调用参数可能包含安全风险，已拦截。请调整参数后重试。";

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                    java.util.function.Function<AgentInput, Flux<AgentEvent>> next) {
        // 工具输入检查在 onActing 中处理
        return next.apply(input);
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx, ActingInput input,
                                     java.util.function.Function<ActingInput, Flux<AgentEvent>> next) {
        // 检查工具调用输入
        try {
            List<ToolUseBlock> toolCalls = extractToolCalls(input);
            if (toolCalls != null) {
                for (ToolUseBlock toolCall : toolCalls) {
                    if (hasDangerousInput(toolCall)) {
                        log.warn("[工具检查] 检测到危险工具输入: user={}, tool={}",
                                ctx.getUserId(), toolCall.getName());
                        // 返回拦截消息
                        return Flux.just(new TextBlockDeltaEvent(null, null, TOOL_CHECK_BLOCKED_MESSAGE));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[工具检查] 检查过程中出现异常，跳过安全检查继续执行: {}", e.getMessage());
            // 出现异常时跳过检查，继续执行（安全失效开放原则）
        }

        // 安全，继续执行
        return next.apply(input);
    }

    /**
     * 从 ActingInput 中提取工具调用列表。
     * <p>
     * ActingInput 是 Java Record，直接通过 toolCalls() 访问器获取，
     * 避免反射带来的脆弱性和静默失败。
     *
     * @param input 执行阶段输入
     * @return 工具调用列表
     */
    private List<ToolUseBlock> extractToolCalls(ActingInput input) {
        if (input == null) {
            return List.of();
        }
        // ActingInput 是 Record，直接调用 toolCalls() 获取工具调用列表
        List<ToolUseBlock> calls = input.toolCalls();
        return calls != null ? calls : List.of();
    }

    /**
     * 检查工具调用输入是否包含危险内容。
     *
     * @param toolCall 工具调用
     * @return 包含危险内容返回 true
     */
    private boolean hasDangerousInput(ToolUseBlock toolCall) {
        Map<String, Object> inputMap = toolCall.getInput();
        if (inputMap == null || inputMap.isEmpty()) {
            return false;
        }

        // 将 Map 转换为字符串进行检查
        String inputStr = inputMap.toString();

        // 检查危险路径
        for (Pattern pattern : DANGEROUS_PATH_PATTERNS) {
            if (pattern.matcher(inputStr).find()) {
                log.warn("[工具检查] 危险路径检测命中: tool={}, pattern={}", toolCall.getName(), pattern.pattern());
                return true;
            }
        }

        // 检查危险命令
        for (Pattern pattern : DANGEROUS_COMMAND_PATTERNS) {
            if (pattern.matcher(inputStr).find()) {
                log.warn("[工具检查] 危险命令检测命中: tool={}, pattern={}", toolCall.getName(), pattern.pattern());
                return true;
            }
        }

        // 检查敏感信息
        for (Pattern pattern : SENSITIVE_INFO_PATTERNS) {
            if (pattern.matcher(inputStr).find()) {
                log.warn("[工具检查] 敏感信息检测命中: tool={}, pattern={}", toolCall.getName(), pattern.pattern());
                return true;
            }
        }

        return false;
    }

    /**
     * 截断字符串（用于日志）。
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}
