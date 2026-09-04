package com.claw.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.common.BizException;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.ResultCode;
import com.claw.agent.common.UserContextHolder;
import com.claw.agent.config.agent.AgentRegistry;
import com.claw.agent.config.agent.ModelCircuitBreaker;
import com.claw.agent.config.infra.ClawProperties;
import com.claw.agent.config.infra.HitlPendingStore;
import com.claw.agent.config.infra.TraceFilter;
import com.claw.agent.mapper.AgentPresetMapper;
import com.claw.agent.mapper.ChatMessageMapper;
import com.claw.agent.mapper.ChatSessionMapper;
import com.claw.agent.model.AgentPipeline;
import com.claw.agent.model.AgentPreset;
import com.claw.agent.model.ChatMessage;
import com.claw.agent.model.ChatSession;
import com.claw.agent.model.ModelProviderConfig;
import com.claw.agent.model.dto.ChatEvent;
import com.claw.agent.model.dto.ChatRequest;
import com.claw.agent.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import com.claw.agent.tool.DocumentParseTools;
import com.claw.agent.tool.OcrTools;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 对话服务：编排 AgentRegistry（满血版 Agent）与前端之间的事件流。
 * <p>
 * 职责：
 * <ul>
 *   <li>会话管理：新会话生成 sessionId 并落库 chat_session（含租户审计字段）</li>
 *   <li>消息入库：用户消息与助手回复逐条落库 chat_message，支持历史会话回看</li>
 *   <li>多模态消息组装：文本 + 图片附件（上传文件转 Base64 DataBlock）</li>
 *   <li>流式对话：streamEvents 转译为前端 SSE 事件（ChatEvent）</li>
 *   <li>HITL：权限暂停时缓存待确认工具调用，用户审批后携带 ConfirmResult 恢复执行</li>
 * </ul>
 * 用户隔离：RuntimeContext(userId, sessionId) 定位状态槽，工作区按 租户/用户 分目录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    /** HITL 待确认缓存（Redis 多实例共享，Redis 不可用时降级为内存） */
    private final HitlPendingStore hitlPendingStore;

    /** 图片类 MIME 前缀（仅图片转为多模态块，其余文件以文本说明方式带入） */
    private static final String MIME_IMAGE_PREFIX = "image/";

    /** 会话标题最大长度 */
    private static final int TITLE_MAX_LENGTH = 60;

    private final AgentRegistry agentRegistry;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AgentPresetMapper agentPresetMapper;
    private final PipelineService pipelineService;
    private final ClawProperties properties;
    private final ObjectMapper objectMapper;
    private final TokenUsageService tokenUsageService;
    private final ConfigService configService;
    private final OcrTools ocrTools;
    private final DocumentParseTools documentParseTools;
    private final SessionSummaryService sessionSummaryService;
    private final PipelineCursorService pipelineCursorService;
    private final ModelCircuitBreaker circuitBreaker;

    /**
     * 发起（或继续）一次流式对话。
     *
     * @param user    当前登录用户
     * @param request 聊天请求（文本 + 可选附件/预设）
     * @return SSE 事件流
     */
    public Flux<ChatEvent> chat(LoginUser user, ChatRequest request) {
        // 同步准备段（校验/会话落库/Agent 解析）是阻塞逻辑，必须切弹性线程池，
        // 否则在 Netty 事件循环上执行 Mapper 查询会阻塞全部请求；
        // 顺带从 Reactor 上下文取出 traceId，桥接进准备段线程的 MDC。
        // onErrorResume 必须包在 defer 外层：准备段抛出的业务异常（如未配置模型提供商）
        // 若逃逸到 SSE 端点之外，会被全局异常处理器转成 JSON Result 返回，
        // 前端按 SSE 解析不到任何事件，界面表现为「无提示卡死」
        return Flux.deferContextual(ctxView -> doChat(user, request,
                        ctxView.getOrDefault(TraceFilter.CONTEXT_KEY, null)))
                .onErrorResume(e -> Flux.just(toErrorEvent(user, request.getSessionId(), e)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 对话执行主体（在弹性线程池上订阅；准备段日志带链路 traceId） */
    private Flux<ChatEvent> doChat(LoginUser user, ChatRequest request, String traceId) {
        // 纯附件消息（无文本）允许发送：用占位文本带入上下文，否则用户只传图片会被 400 拒绝且无提示
        boolean hasAttachments = request.getAttachments() != null && !request.getAttachments().isEmpty();
        if (!StringUtils.hasText(request.getContent())) {
            if (!hasAttachments) {
                throw new BizException(ResultCode.PARAM_ERROR, "消息内容不能为空");
            }
            request.setContent("[用户上传了附件，请结合附件内容作答]");
        }
        String initialSessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId() : UUID.randomUUID().toString().replace("-", "");
        // 同步段写入用户上下文：会话落库的审计填充（创建人/修改人）依赖它，结束后立即清理；
        // MDC 同理桥接：准备段日志带链路 traceId（流式段线程持续切换，不在覆盖范围）
        UserContextHolder.set(user);
        ReactiveSupport.putTrace(traceId);
        try {
            // 若前端传入的 sessionId 对应上一回合以 HITL 暂停结束且用户未审批，
            // AgentScope 会从状态存储恢复 ASKING 态并直接抛 IllegalStateException；
            // 此处检测内存缓存中的待确认条目，若有则视为用户放弃旧回合，启用新 sessionId
            String effectiveSessionId;
            if (StringUtils.hasText(request.getSessionId())
                    && hitlPendingStore.hasPending(user.getUsername(), request.getSessionId())) {
                effectiveSessionId = UUID.randomUUID().toString().replace("-", "");
                hitlPendingStore.delete(user.getUsername(), request.getSessionId());
                log.info("检测到旧 HITL 残留，启用新 sessionId: old={}, new={}", request.getSessionId(), effectiveSessionId);
            } else {
                effectiveSessionId = initialSessionId;
            }
            ensureSession(user, effectiveSessionId, request.getContent());
            // 用户消息先落库：即使后续模型调用失败，历史会话也能回看到用户问了什么；
            // 落库走异步线程，失败仅告警不影响对话主链路（历史回看是辅助能力，非关键路径）
            persistMessage(user, effectiveSessionId, ChatMessage.ROLE_USER, request.getContent(),
                    request.getAttachments(), ChatMessage.STATUS_OK);
            HarnessAgent agent;
            UserMessage msg;
            RuntimeContext rc;
            ModelProviderConfig providerCfg;
            // 流水线是消息级剧本注入（与预设人格正交）：不影响 Agent 缓存键，
            // 仅把步骤/异常策略拼进当轮用户消息，避免人格与剧本耦合污染长期记忆
            // 流水线解析：优先使用请求中的 pipelineCode；未指定时自动检测未完成的游标进行断点续跑
            AgentPipeline pipeline;
            boolean isResume = false;
            if (StringUtils.hasText(request.getPipelineCode())) {
                pipeline = resolvePipeline(user, request.getPipelineCode());
            } else {
                // 自动续跑检测：同一会话中上次流水线未完成 → 重新注入剧本 + 标记已完成步骤
                PipelineCursorService.CursorData cursor =
                        pipelineCursorService.loadCursor(user.getUserId(), effectiveSessionId);
                if (pipelineCursorService.isUnfinished(cursor)) {
                    pipeline = resolvePipeline(user, cursor.getPipelineCode());
                    isResume = true;
                    log.info("自动续跑流水线: user={}, session={}, pipeline={}, completedSteps={}/{}",
                            user.getUsername(), effectiveSessionId,
                            cursor.getPipelineCode(), cursor.getCompletedSteps(), cursor.getTotalSteps());
                } else {
                    pipeline = null;
                }
            }
            try {
                agent = resolveAgent(user, request.getPresetCode());
                // 提前创建 RuntimeContext + 解析模型配置，供 buildUserMessage 中 OCR 预处理使用
                rc = runtimeContext(user, effectiveSessionId);
                providerCfg = configService.resolveCurrentProvider(user.getTenantId(), user.getUserId());
                // 熔断检查：提供商连续失败时快速拒绝，避免等 3 次重试超时（6 分钟）才报错
                if (!circuitBreaker.isAvailable(providerCfg.getProvider())) {
                    throw new BizException(ResultCode.AGENT_ERROR,
                            circuitBreaker.getRejectionMessage(providerCfg.getProvider()));
                }
                msg = buildUserMessage(user, request, pipeline, rc, providerCfg);
            } catch (RuntimeException e) {
                // 准备段异常（如模型提供商未配置 API key）发生在流式段之前，
                // 流内的失败落库钩子不会触发，此处补存一条助手失败消息保证历史回看完整；
                // 异常继续上抛由外层 onErrorResume 转 SSE error 事件
                persistMessage(user, effectiveSessionId, ChatMessage.ROLE_ASSISTANT,
                        friendlyMessage(e), null, ChatMessage.STATUS_FAIL);
                throw e;
            }
            // providerCfg 已在 buildUserMessage 前解析，此处复用
            String providerName = providerCfg.getProvider();
            String modelName = providerCfg.getModelName();

            log.debug("发起对话: user={}, session={}, preset={}, pipeline={}",
                    user.getUsername(), effectiveSessionId, request.getPresetCode(), request.getPipelineCode());
            // 流式段累积助手回复，回合结束（end 事件）一次性落库；
            // 中途异常时把已产出部分以失败状态落库，不丢用户已看到的输出。
            // 局部变量只属于本次流订阅，多回合并发互不干扰（同会话由 RuntimeContext 串行化）
            StringBuilder replyBuffer = new StringBuilder();
            // 工具调用追踪：记录本轮对话中调用的所有工具名称（用于 Token 日志）
            List<String> toolNames = new ArrayList<>();
            // 流水线进度追踪：pipeline != null 时启用，每次 ModelCallEndEvent 计数 +1
            // 进度 = min(当前轮次, 总步数) / 总步数，前端展示进度条
            final int pipelineTotalSteps = pipeline != null
                    ? countPipelineSteps(pipeline.getSteps()) : 0;
            final AtomicInteger pipelineModelCallCount = new AtomicInteger(0);
            if (pipeline != null) {
                if (!isResume) {
                    // 新流水线启动：清理旧游标，保存新游标
                    pipelineCursorService.clearCursor(user.getUserId(), effectiveSessionId);
                    pipelineCursorService.saveCursor(user.getUserId(), effectiveSessionId,
                            pipeline.getPipelineCode(), pipeline.getPipelineName(), pipelineTotalSteps);
                }
                log.debug("流水线准备就绪: user={}, session={}, totalSteps={}, resume={}",
                        user.getUsername(), effectiveSessionId, pipelineTotalSteps, isResume);
            }
            // keepalive 机制：Flux.merge 侧通道每 15s 发心跳，与事件流并行。
            // 相比 concatMap+delaySubscription 模式，不创建内部 Flux 队列，
            // 长流水线（25 轮迭代）无定时器堆积。主事件流终止时 takeUntilOther 同步停止心跳。
            Flux<ChatEvent> eventStream = agent.streamEvents(List.of(msg), rc)
                    .doOnNext(event -> {
                        accumulateAndFlush(user, effectiveSessionId,
                                toChatEvent(user, effectiveSessionId, event, providerName, modelName, toolNames,
                                        pipeline, pipelineTotalSteps, pipelineModelCallCount),
                                replyBuffer);
                        if (event instanceof ToolCallStartEvent toolStart) {
                            String toolName = toolStart.getToolCallName();
                            if (toolName != null && !toolNames.contains(toolName)) {
                                toolNames.add(toolName);
                            }
                        }
                    })
                    .map(event -> toChatEvent(user, effectiveSessionId, event, providerName, modelName, toolNames,
                            pipeline, pipelineTotalSteps, pipelineModelCallCount))
                    .share();  // 热流：merge 与 takeUntilOther 共享同一订阅，避免 streamEvents 被重复执行
            Flux<ChatEvent> heartbeat = Flux.interval(Duration.ofSeconds(15))
                    .map(t -> ChatEvent.builder().type("keepalive")
                            .sessionId(effectiveSessionId).build());
            Flux<ChatEvent> withKeepalive = Flux.merge(eventStream, heartbeat)
                    .takeUntilOther(eventStream.ignoreElements());
            return withKeepalive
                    .doOnComplete(() -> {
                        log.debug("Flux 完成：user={}, session={}", user.getUsername(), effectiveSessionId);
                        circuitBreaker.recordSuccess(providerName);
                    })
                    .doFinally(signal -> log.debug("Flux 终止信号：user={}, session={}, signal={}", user.getUsername(), effectiveSessionId, signal))
                    .onErrorResume(e -> {
                        log.error("对话执行异常：user={}, session={}", user.getUsername(), effectiveSessionId, e);
                        circuitBreaker.recordFailure(providerName);
                        persistMessage(user, effectiveSessionId, ChatMessage.ROLE_ASSISTANT,
                                failureContent(replyBuffer.toString(), e), null, ChatMessage.STATUS_FAIL);
                        return Flux.just(toErrorEvent(user, effectiveSessionId, e));
                    });
        } finally {
            UserContextHolder.clear();
            MDC.remove(TraceFilter.MDC_KEY);
        }
    }

    /**
     * HITL 确认：用户对待确认工具做出允许/拒绝决策后恢复执行。
     *
     * @param user      当前登录用户
     * @param sessionId 会话ID
     * @param approved  是否批准
     * @return 恢复后的 SSE 事件流
     */
    public Flux<ChatEvent> confirm(LoginUser user, String sessionId, boolean approved) {
        // 与 chat 同理：同步准备段切弹性线程池，避免阻塞事件循环；顺带桥接 traceId 进 MDC；
        // 外层 onErrorResume 兜住准备段异常（如「没有待确认的工具调用」），转为 SSE error 事件而非逃逸
        return Flux.deferContextual(ctxView -> doConfirm(user, sessionId, approved,
                        ctxView.getOrDefault(TraceFilter.CONTEXT_KEY, null)))
                .onErrorResume(e -> Flux.just(toErrorEvent(user, sessionId, e)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** HITL 恢复执行主体（在弹性线程池上订阅；准备段日志带链路 traceId） */
    private Flux<ChatEvent> doConfirm(LoginUser user, String sessionId, boolean approved, String traceId) {
        ReactiveSupport.putTrace(traceId);
        try {
            return doConfirmInternal(user, sessionId, approved);
        } finally {
            MDC.remove(TraceFilter.MDC_KEY);
        }
    }

    /** HITL 恢复执行内部实现（同步准备 + 恢复流组装） */
    private Flux<ChatEvent> doConfirmInternal(LoginUser user, String sessionId, boolean approved) {
        List<ToolUseBlock> pending = hitlPendingStore.remove(user.getUsername(), sessionId);
        if (pending == null || pending.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "当前会话没有待确认的工具调用");
        }
        // 逐个工具构建确认结果（整体批准/拒绝）
        List<ConfirmResult> confirmResults = pending.stream()
                .map(toolCall -> new ConfirmResult(approved, toolCall))
                .toList();
        Map<String, Object> meta = new HashMap<>();
        meta.put(Msg.METADATA_CONFIRM_RESULTS, confirmResults);
        Msg resumeMsg = Msg.builder()
                .name(user.getUsername())
                .role(MsgRole.USER)
                .textContent(approved ? "approved" : "denied")
                .metadata(meta)
                .build();

        HarnessAgent agent;
        RuntimeContext rc;
        try {
            agent = resolveAgent(user, null);
            rc = runtimeContext(user, sessionId);
        } catch (RuntimeException e) {
            // 准备段异常补存助手失败消息（与主对话轮同策略），异常上抛转 error 事件
            persistMessage(user, sessionId, ChatMessage.ROLE_ASSISTANT,
                    friendlyMessage(e), null, ChatMessage.STATUS_FAIL);
            throw e;
        }
        log.debug("HITL 确认恢复: user={}, session={}, approved={}, toolCount={}",
                user.getUsername(), sessionId, approved, pending.size());
        // 回合级缓存模型提供商信息
        ModelProviderConfig providerCfg = configService.resolveCurrentProvider(user.getTenantId(), user.getUserId());
        if (!circuitBreaker.isAvailable(providerCfg.getProvider())) {
            throw new BizException(ResultCode.AGENT_ERROR,
                    circuitBreaker.getRejectionMessage(providerCfg.getProvider()));
        }
        String providerName = providerCfg.getProvider();
        String modelName = providerCfg.getModelName();
        // 恢复流同样累积回复并落库（本轮续接输出与主对话轮同等待遇）
        StringBuilder replyBuffer = new StringBuilder();
        // HITL 恢复执行也跟踪工具调用（HITL 无流水线上下文，进度追踪参数传空值）
        List<String> toolNames = new ArrayList<>();
        return agent.streamEvents(List.of(resumeMsg), rc)
                .map(event -> {
                    ChatEvent chatEvent = toChatEvent(user, sessionId, event, providerName, modelName, toolNames,
                            null, 0, new AtomicInteger(0));
                    accumulateAndFlush(user, sessionId, chatEvent, replyBuffer);
                    // 跟踪工具调用
                    if (event instanceof ToolCallStartEvent toolStart) {
                        String toolName = toolStart.getToolCallName();
                        if (toolName != null && !toolNames.contains(toolName)) {
                            toolNames.add(toolName);
                        }
                    }
                    return chatEvent;
                })
                .doOnComplete(() -> circuitBreaker.recordSuccess(providerName))
                .onErrorResume(e -> {
                    log.error("HITL 恢复执行异常: user={}, session={}", user.getUsername(), sessionId, e);
                    circuitBreaker.recordFailure(providerName);
                    persistMessage(user, sessionId, ChatMessage.ROLE_ASSISTANT,
                            failureContent(replyBuffer.toString(), e), null, ChatMessage.STATUS_FAIL);
                    return Flux.just(ChatEvent.builder().type("error")
                            .sessionId(sessionId).message("恢复执行失败：" + e.getMessage()).build());
                });
    }

    /**
     * 查询当前用户的会话列表（倒序）。
     * <p>
     * 默认只返回活跃会话（archived=0），传 archived=true 返回已归档会话。
     *
     * @param user     当前登录用户
     * @param archived 是否查询已归档（true=仅归档，false=仅活跃）
     * @return 会话列表
     */
    public List<ChatSession> listSessions(LoginUser user, boolean archived) {
        return chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUsername, user.getUsername())
                        .eq(ChatSession::getArchived, archived ? 1 : 0)
                        .orderByDesc(ChatSession::getUpdateTime)
                        .last("LIMIT 200"));
    }

    /**
     * 按关键词搜索会话：在用户的聊天消息中模糊匹配，返回命中的会话列表。
     * <p>
     * 搜索范围：用户消息 + 助手回复的 content 字段（LIKE 模糊匹配）。
     * 结果按消息最近匹配时间倒序，最多返回 20 条。
     *
     * @param user    当前登录用户
     * @param keyword 搜索关键词（至少 1 个字符）
     * @return 匹配的会话列表（去重，按最近匹配消息时间倒序）
     */
    public List<ChatSession> searchSessions(LoginUser user, String keyword) {
        if (!StringUtils.hasText(keyword) || keyword.isBlank()) {
            return List.of();
        }
        // 先从消息表中查出匹配的 sessionId（去重），再查会话详情
        List<ChatMessage> matchedMessages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getUsername, user.getUsername())
                        .like(ChatMessage::getContent, keyword.trim())
                        .orderByDesc(ChatMessage::getCreateTime)
                        .last("LIMIT 100"));
        if (matchedMessages.isEmpty()) {
            return List.of();
        }
        // 按 sessionId 去重，保留首次出现（即最新消息）的顺序
        List<String> sessionIds = matchedMessages.stream()
                .map(ChatMessage::getSessionId)
                .distinct()
                .limit(20)
                .toList();
        // 查询会话元数据（保持 sessionIds 的顺序）
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUsername, user.getUsername())
                        .in(ChatSession::getSessionId, sessionIds)
                        .eq(ChatSession::getArchived, 0));
        // 按 sessionIds 顺序排序（保持匹配相关度）
        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < sessionIds.size(); i++) {
            orderMap.put(sessionIds.get(i), i);
        }
        sessions.sort((a, b) -> orderMap.getOrDefault(a.getSessionId(), 999)
                - orderMap.getOrDefault(b.getSessionId(), 999));
        return sessions;
    }

    /**
     * 查询指定会话的聊天记录（时间正序）。
     * <p>
     * 先按「用户名 + sessionId」校验会话归属，防止构造他人 sessionId 越权读取对话内容。
     *
     * @param user      当前登录用户（查询对象由 JWT 定位，不信任前端传参）
     * @param sessionId AgentScope 会话ID
     * @return 消息列表（正序，前端按序渲染）
     */
    public List<ChatMessage> listMessages(LoginUser user, String sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUsername, user.getUsername())
                        .last("LIMIT 1"));
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在或无权访问");
        }
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getUsername, user.getUsername())
                        .orderByAsc(ChatMessage::getId)
                        .last("LIMIT 200"));
    }

    /**
     * 导出会话为 Markdown 格式文本。
     * <p>
     * 按消息角色分节（用户/助手），附带时间戳，便于离线阅读与存档。
     *
     * @param user      当前登录用户
     * @param sessionId AgentScope 会话ID
     * @return Markdown 格式文本
     */
    public String exportAsMarkdown(LoginUser user, String sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUsername, user.getUsername())
                        .last("LIMIT 1"));
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在或无权访问");
        }
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getUsername, user.getUsername())
                        .orderByAsc(ChatMessage::getId));

        StringBuilder sb = new StringBuilder();
        // 标题：会话标题 + 时间范围
        String title = StringUtils.hasText(session.getTitle()) ? session.getTitle() : "未命名会话";
        sb.append("# ").append(title).append("\n\n");
        sb.append("> 导出时间：").append(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        sb.append("---\n\n");

        java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (ChatMessage msg : messages) {
            String role = ChatMessage.ROLE_USER.equals(msg.getRole()) ? "用户" : "助手";
            String time = msg.getCreateTime() != null ? msg.getCreateTime().format(timeFmt) : "";
            sb.append("## ").append(role);
            if (!time.isEmpty()) {
                sb.append("（").append(time).append("）");
            }
            sb.append("\n\n");
            sb.append(msg.getContent() != null ? msg.getContent() : "（无内容）");
            sb.append("\n\n---\n\n");
        }
        return sb.toString();
    }

    /** 删除会话（元数据 + 聊天记录一并清理；AgentScope 状态由过期策略清理） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(LoginUser user, String sessionId) {
        chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUsername, user.getUsername())
                .eq(ChatSession::getSessionId, sessionId));
        // 级联删除消息记录，避免会话删除后留下孤儿数据（归属校验同用户名条件）
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUsername, user.getUsername())
                .eq(ChatMessage::getSessionId, sessionId));
        hitlPendingStore.delete(user.getUsername(), sessionId);
    }

    /**
     * 归档会话：将活跃会话标记为已归档（隐藏但保留）。
     * <p>
     * 归档后会话从主列表消失，可在「归档」Tab 查看、取消归档或彻底删除。
     *
     * @param user      当前登录用户
     * @param sessionId AgentScope 会话ID
     */
    public void archiveSession(LoginUser user, String sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUsername, user.getUsername())
                        .last("LIMIT 1"));
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在或无权访问");
        }
        session.setArchived(1);
        chatSessionMapper.updateById(session);
    }

    /**
     * 取消归档：将已归档会话恢复为活跃状态。
     *
     * @param user      当前登录用户
     * @param sessionId AgentScope 会话ID
     */
    public void unarchiveSession(LoginUser user, String sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUsername, user.getUsername())
                        .last("LIMIT 1"));
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在或无权访问");
        }
        session.setArchived(0);
        chatSessionMapper.updateById(session);
    }

    /**
     * 修改会话标题。
     * <p>
     * 按用户名 + sessionId 定位会话（防越权），仅更新 title 字段。
     *
     * @param user      当前登录用户
     * @param sessionId AgentScope 会话ID
     * @param newTitle  新标题（去除首尾空白后不可为空）
     */
    public void renameSession(LoginUser user, String sessionId, String newTitle) {
        if (!StringUtils.hasText(newTitle) || newTitle.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "标题不能为空");
        }
        String trimmed = newTitle.trim();
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            throw new BizException(ResultCode.PARAM_ERROR, "标题不能超过" + TITLE_MAX_LENGTH + "个字符");
        }
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUsername, user.getUsername())
                        .last("LIMIT 1"));
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在或无权访问");
        }
        session.setTitle(trimmed);
        chatSessionMapper.updateById(session);
    }

    /**
     * 重新生成：删除会话最后一轮助手回复，重新发送最后一条用户消息。
     * <p>
     * 场景：用户对上次回复不满意，希望模型重新回答同一个问题。
     * 实现：找到最后一条用户消息 → 删除其后的助手消息 → 以该用户消息发起新对话。
     *
     * @param user      当前登录用户
     * @param sessionId 会话ID
     * @return SSE 事件流（与 chat 接口行为一致）
     */
    public Flux<ChatEvent> regenerate(LoginUser user, String sessionId) {
        return Flux.deferContextual(ctxView -> doRegenerate(user, sessionId,
                        ctxView.getOrDefault(TraceFilter.CONTEXT_KEY, null)))
                .onErrorResume(e -> Flux.just(toErrorEvent(user, sessionId, e)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 重新生成执行主体（在弹性线程池上订阅） */
    private Flux<ChatEvent> doRegenerate(LoginUser user, String sessionId, String traceId) {
        ReactiveSupport.putTrace(traceId);
        try {
            // 1. 查找最后一条用户消息
            ChatMessage lastUserMsg = chatMessageMapper.selectOne(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, sessionId)
                            .eq(ChatMessage::getUsername, user.getUsername())
                            .eq(ChatMessage::getRole, ChatMessage.ROLE_USER)
                            .orderByDesc(ChatMessage::getId)
                            .last("LIMIT 1"));
            if (lastUserMsg == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "会话中没有用户消息，无法重新生成");
            }
            // 2. 删除该用户消息之后的所有助手回复（重新生成）
            chatMessageMapper.delete(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, sessionId)
                            .eq(ChatMessage::getUsername, user.getUsername())
                            .eq(ChatMessage::getRole, ChatMessage.ROLE_ASSISTANT)
                            .gt(ChatMessage::getId, lastUserMsg.getId()));
            log.info("重新生成: user={}, session={}, lastUserMsgId={}",
                    user.getUsername(), sessionId, lastUserMsg.getId());
            // 3. 构建 ChatRequest 并复用 chat 流程
            ChatRequest request = new ChatRequest();
            request.setSessionId(sessionId);
            request.setContent(lastUserMsg.getContent());
            // 解析附件
            if (StringUtils.hasText(lastUserMsg.getAttachments())) {
                try {
                    List<String> attachments = objectMapper.readValue(
                            lastUserMsg.getAttachments(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    request.setAttachments(attachments);
                } catch (Exception e) {
                    log.warn("重新生成解析附件失败，忽略: session={}", sessionId, e);
                }
            }
            return doChat(user, request, traceId);
        } finally {
            MDC.remove(TraceFilter.MDC_KEY);
        }
    }

    // ------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------

    /** 解析用户 Agent（含预设人格叠加） */
    private HarnessAgent resolveAgent(LoginUser user, String presetCode) {
        String presetPrompt = null;
        if (StringUtils.hasText(presetCode)) {
            AgentPreset preset = agentPresetMapper.selectOne(
                    new LambdaQueryWrapper<AgentPreset>()
                            .eq(AgentPreset::getAgentCode, presetCode)
                            .eq(AgentPreset::getEnabled, 1)
                            .and(w -> w
                                    .eq(AgentPreset::getScope, AgentPreset.SCOPE_PLATFORM)
                                    .or(o -> o.eq(AgentPreset::getScope, AgentPreset.SCOPE_TENANT)
                                            .eq(AgentPreset::getTenantId, user.getTenantId()))
                                    .or(o -> o.eq(AgentPreset::getScope, AgentPreset.SCOPE_USER)
                                            .eq(AgentPreset::getTenantId, user.getTenantId())
                                            .eq(AgentPreset::getOwnerId, user.getUserId())))
                            .last("LIMIT 1"));
            if (preset == null) {
                throw new BizException(ResultCode.PRESET_DISABLED, "预设模板不存在或已禁用: " + presetCode);
            }
            presetPrompt = preset.getSysPrompt();
        }
        return agentRegistry.getOrCreate(user.getUserId(), user.getTenantId(), presetCode, presetPrompt);
    }

    /**
     * 按编码解析当前用户可用的启用流水线。
     *
     * @param user         当前登录用户
     * @param pipelineCode 流水线编码（为空表示本轮不启用剧本）
     * @return 命中的流水线；未指定编码时返回 null
     * @throws BizException 编码非空但不可见或已禁用时抛出
     */
    private AgentPipeline resolvePipeline(LoginUser user, String pipelineCode) {
        if (!StringUtils.hasText(pipelineCode)) {
            return null;
        }
        AgentPipeline pipeline = pipelineService.resolveEnabled(user, pipelineCode);
        if (pipeline == null) {
            throw new BizException(ResultCode.PIPELINE_DISABLED, "流水线不存在或已禁用: " + pipelineCode);
        }
        return pipeline;
    }

    /**
     * 组装多模态用户消息：文本 + 图片附件（非图片文件以说明文本带入）+ 可选流水线剧本。
     * <p>
     * 当模型不支持视觉时，自动调用 OCR 提取图片文字，用 TextBlock 替换 DataBlock，
     * 使任意文本模型都能处理图片内容（OCR 失败时降级为文件路径说明）。
     */
    private UserMessage buildUserMessage(LoginUser user, ChatRequest request, AgentPipeline pipeline,
                                         RuntimeContext rc, ModelProviderConfig providerCfg) {
        List<ContentBlock> blocks = new ArrayList<>();
        // 剧本置于首位：先约束执行框架，再给出用户原始诉求与附件，保证模型按步骤展开
        if (pipeline != null) {
            // 检查是否续跑模式：同一会话存在未完成游标 → 注入续跑剧本（标记已完成步骤）
            PipelineCursorService.CursorData cursor =
                    pipelineCursorService.loadCursor(user.getUserId(), rc.getSessionId());
            if (pipelineCursorService.isUnfinished(cursor)) {
                blocks.add(TextBlock.builder()
                        .text(buildResumePipelineScript(pipeline, cursor)).build());
            } else {
                blocks.add(TextBlock.builder().text(buildPipelineScript(pipeline)).build());
            }
        }
        blocks.add(TextBlock.builder().text(request.getContent()).build());
        boolean visionSupported = supportsVision(providerCfg);
        if (request.getAttachments() != null) {
            for (String fileName : request.getAttachments()) {
                appendAttachment(user, fileName, blocks, visionSupported, rc);
            }
        }
        return new UserMessage(user.getUsername(), blocks.toArray(new ContentBlock[0]));
    }

    /**
     * 拼装续跑剧本文本：标记已完成步骤，要求 Agent 从断点继续。
     * <p>
     * 与 {@link #buildPipelineScript} 不同，续跑剧本包含「已完成步骤」上下文，
     * Agent 可据此跳过已执行部分，避免重复工作。
     */
    private String buildResumePipelineScript(AgentPipeline pipeline,
                                             PipelineCursorService.CursorData cursor) {
        int totalSteps = countPipelineSteps(pipeline.getSteps());
        int completedSteps = Math.min(cursor.getCompletedSteps(), totalSteps);
        StringBuilder sb = new StringBuilder();
        sb.append("【流水线：").append(pipeline.getPipelineName())
                .append("（共 ").append(totalSteps).append(" 步）")
                .append("】\n\n");
        sb.append("⚠️ 上次执行在步骤 ").append(completedSteps + 1).append(" 中断，");
        sb.append("步骤 1-").append(completedSteps).append(" 已完成。");
        sb.append("请从步骤 ").append(completedSteps + 1).append(" 继续执行，");
        sb.append("不要重复已完成的步骤。\n\n");
        sb.append("## 输出要求\n");
        sb.append("- 每完成一个步骤后，立即输出该步骤的结果。\n");
        sb.append("- 每个步骤的结果用二级标题分隔，格式：## 步骤 N：步骤名称\n");
        sb.append("- 如果某步失败，记录失败原因后继续执行后续步骤。\n\n");
        sb.append("## 执行步骤\n");
        sb.append(pipeline.getSteps());
        if (StringUtils.hasText(pipeline.getExceptionHandling())) {
            sb.append("\n\n## 异常处理策略\n").append(pipeline.getExceptionHandling());
        }
        return sb.toString();
    }

    /** 拼装流水线剧本文本：名称 + 步骤 + 增量输出指令 + 异常处理策略 */
    private String buildPipelineScript(AgentPipeline pipeline) {
        int totalSteps = countPipelineSteps(pipeline.getSteps());
        StringBuilder sb = new StringBuilder();
        sb.append("【流水线：").append(pipeline.getPipelineName())
                .append("（共 ").append(totalSteps).append(" 步）")
                .append("】请严格按以下剧本执行本次任务，逐步完成每个步骤。\n\n")
                // 增量输出指令：要求 Agent 每完成一步立即输出中间结果，而非等全部完成
                .append("## 输出要求\n")
                .append("- 每完成一个步骤后，立即输出该步骤的结果（不要等所有步骤完成再统一输出）。\n")
                .append("- 每个步骤的结果用二级标题分隔，格式：## 步骤 N：步骤名称\n")
                .append("- 如果某步失败，记录失败原因后继续执行后续步骤。\n\n")
                .append("## 执行步骤\n")
                .append(pipeline.getSteps());
        if (StringUtils.hasText(pipeline.getExceptionHandling())) {
            sb.append("\n\n## 异常处理策略\n").append(pipeline.getExceptionHandling());
        }
        return sb.toString();
    }

    /**
     * 从流水线步骤 Markdown 中解析步骤总数。
     * 匹配模式：行首 "Step N" / "步骤 N" / "### Step N" 等（不区分大小写）。
     */
    private int countPipelineSteps(String stepsMarkdown) {
        if (!StringUtils.hasText(stepsMarkdown)) {
            return 1;
        }
        // 匹配 "Step 1" / "步骤 1" / "Step1" 等模式
        Matcher matcher = Pattern.compile("(?im)^(?:#{1,6}\\s+)?(?:step|步骤)\\s*(\\d+)").matcher(stepsMarkdown);
        int maxStep = 0;
        while (matcher.find()) {
            int stepNum = Integer.parseInt(matcher.group(1));
            maxStep = Math.max(maxStep, stepNum);
        }
        return Math.max(maxStep, 1);
    }

    /**
     * 单个附件处理：图片根据模型能力选择 DataBlock 或 OCR 文字，其它文件附说明文本。
     * <p>
     * 视觉模型：图片转为 Base64 DataBlock，由模型直接识别；
     * 非视觉模型：自动调用 OCR 提取文字，以 TextBlock 形式带入（OCR 失败时降级为文件路径说明）。
     */
    private void appendAttachment(LoginUser user, String fileName, List<ContentBlock> blocks,
                                  boolean visionSupported, RuntimeContext rc) {
        try {
            // 防目录穿越：fileName 来自前端传参，../ 可越到他人目录读取文件（信息泄漏）
            Path userDir = Paths.get(properties.getUpload().getDir(),
                    user.getUsername()).toAbsolutePath().normalize();
            Path file = userDir.resolve(fileName).normalize();
            if (!file.startsWith(userDir)) {
                log.warn("附件路径非法，跳过: user={}, fileName={}", user.getUsername(), fileName);
                return;
            }
            if (!Files.exists(file)) {
                log.warn("附件不存在，跳过: {}", file);
                return;
            }
            String mediaType = guessMediaType(fileName);
            if (mediaType.startsWith(MIME_IMAGE_PREFIX)) {
                if (visionSupported) {
                    // 视觉模型：直接发送图片 Base64
                    byte[] bytes = Files.readAllBytes(file);
                    blocks.add(DataBlock.builder()
                            .source(Base64Source.builder()
                                    .data(Base64.getEncoder().encodeToString(bytes))
                                    .mediaType(mediaType)
                                    .build())
                            .build());
                } else {
                    // 非视觉模型：自动 OCR 提取文字，替代原始图片
                    String ocrText = ocrTools.recognizeText(file.toAbsolutePath().toString(), null, rc);
                    if (ocrText != null && !ocrText.isBlank()) {
                        blocks.add(TextBlock.builder()
                                .text("[用户上传了图片：" + fileName + "，OCR 识别结果如下]\n" + ocrText).build());
                        log.debug("图片 OCR 预处理成功: user={}, file={}", user.getUsername(), fileName);
                    } else {
                        // OCR 未返回有效文字（可能未配置凭证或识别失败），降级为文件路径说明
                        blocks.add(TextBlock.builder()
                                .text("[用户上传了图片：" + fileName + "（位于工作区上传目录，当前模型不支持图片输入且 OCR 未返回结果）]").build());
                        log.warn("图片 OCR 未返回有效文字，降级处理: user={}, file={}", user.getUsername(), fileName);
                    }
                }
            } else {
                // 非图片文件：使用 Tika 提取文本内容
                String parsedText = documentParseTools.parseDocument(file.toAbsolutePath().toString());
                if (parsedText != null && !parsedText.startsWith("错误") && !parsedText.startsWith("文档解析失败")) {
                    blocks.add(TextBlock.builder()
                            .text("[用户上传了文件：" + fileName + "，文档解析结果如下]\n" + parsedText).build());
                    log.debug("文档解析成功: user={}, file={}", user.getUsername(), fileName);
                } else {
                    // 解析失败（扫描件/超大文件等），降级为文件路径说明
                    blocks.add(TextBlock.builder()
                            .text("[用户上传了文件：" + fileName + "（位于工作区上传目录，文档解析未返回有效内容）]\n" + parsedText).build());
                    log.warn("文档解析未返回有效内容: user={}, file={}, result={}", user.getUsername(), fileName, parsedText);
                }
            }
        } catch (Exception e) {
            log.warn("附件处理失败，跳过: {}", fileName, e);
        }
    }

    /**
     * 异常转 SSE error 事件：业务异常透出友好消息，系统异常不暴露内部细节。
     * 准备段与流式段共用，保证前端任何失败路径都能收到可见提示。
     *
     * @param user      当前登录用户（预留，便于后续按用户定制提示）
     * @param sessionId 会话ID（可能为空，首次对话尚未生成时）
     * @param e         异常
     * @return error 类型的 SSE 事件
     */
    private ChatEvent toErrorEvent(LoginUser user, String sessionId, Throwable e) {
        return ChatEvent.builder().type("error")
                .sessionId(StringUtils.hasText(sessionId) ? sessionId : null)
                .message(friendlyMessage(e)).build();
    }

    /** 异常友好文案：业务异常透出原始消息，系统异常不暴露内部细节 */
    private String friendlyMessage(Throwable e) {
        if (e instanceof BizException) {
            return e.getMessage();
        }
        String msg = e.getMessage();
        // 模型不支持图片：提示用户切换视觉模型
        if (msg != null && msg.contains("does not support image")) {
            return "当前模型不支持图片输入，请切换到支持视觉的模型（如 qwen-vl-max、gpt-4o 等）后重试";
        }
        return "对话执行失败，请稍后重试（" + (msg == null
                ? e.getClass().getSimpleName() : msg) + "）";
    }

    /**
     * 流式段异常的落库内容：已产出正文 + 失败原因。
     * <p>
     * 此前只落已产出部分：无产出时内容为空被跳过落库、失败原因也一并丢失，
     * 导致历史回看既看不到错误也没有失败记录；现在把原因一并固化进 content。
     */
    private String failureContent(String partial, Throwable e) {
        String reason = "[执行失败] " + friendlyMessage(e);
        return StringUtils.hasText(partial) ? partial + "\n\n" + reason : reason;
    }

    /**
     * AgentScope 事件 → 前端 SSE 事件。
     * <p>
     * 流水线进度追踪：当 pipeline != null 时，每次 ModelCallEndEvent 计数 +1，
     * 发射 progress 事件（step = min(当前轮次, 总步数), total = 总步数）。
     * 前端根据 step/total 渲染进度条。
     */
    private ChatEvent toChatEvent(LoginUser user, String sessionId, AgentEvent event,
                                  String providerName, String modelName, List<String> toolNames,
                                  AgentPipeline pipeline, int pipelineTotalSteps,
                                  AtomicInteger pipelineModelCallCount) {
        ChatEvent.ChatEventBuilder builder = ChatEvent.builder().sessionId(sessionId);
        if (event instanceof AgentStartEvent start) {
            return builder.type("start").replyId(start.getReplyId()).build();
        } else if (event instanceof TextBlockDeltaEvent delta) {
            return builder.type("text").replyId(delta.getReplyId()).delta(delta.getDelta()).build();
        } else if (event instanceof ThinkingBlockStartEvent ts) {
            // 思考开始：前端切换思考区域
            return builder.type("thinking_start").replyId(ts.getReplyId()).build();
        } else if (event instanceof ThinkingBlockDeltaEvent td) {
            // 思考增量：前端追加思考文本
            return builder.type("thinking").replyId(td.getReplyId()).delta(td.getDelta()).build();
        } else if (event instanceof ThinkingBlockEndEvent te) {
            // 思考结束：前端关闭思考区域
            return builder.type("thinking_end").replyId(te.getReplyId()).build();
        } else if (event instanceof ToolCallStartEvent tc) {
            return builder.type("tool_start").toolCallId(tc.getToolCallId())
                    .toolName(tc.getToolCallName()).build();
        } else if (event instanceof ToolResultEndEvent end) {
            return builder.type("tool_end").toolCallId(end.getToolCallId())
                    .state(String.valueOf(end.getState())).build();
        } else if (event instanceof ModelCallEndEvent modelEnd) {
            // Token 自动追踪：使用回合级缓存的 provider/modelName，避免重复查库
            // 工具名称从外层 toolNames 列表传入（已在 map 中跟踪）
            recordTokenUsage(user, sessionId, modelEnd, providerName, modelName, toolNames);
            // 流水线进度事件：每次模型调用完成计数 +1，前端展示进度条
            // step = min(当前轮次, 总步数)：Agent 可能在最后几轮做综合总结，进度不应超过 100%
            if (pipeline != null && pipelineTotalSteps > 0) {
                int currentCall = pipelineModelCallCount.incrementAndGet();
                int currentStep = Math.min(currentCall, pipelineTotalSteps);
                // 同步游标进度到 Redis：断点续跑时依赖此数据定位已完成步骤
                pipelineCursorService.updateProgress(user.getUserId(), sessionId, currentStep);
                return builder.type("progress")
                        .progressStep(currentStep)
                        .progressTotal(pipelineTotalSteps)
                        .progressLabel(pipeline.getPipelineName())
                        .build();
            }
            return builder.type("ignore").build();
        } else if (event instanceof RequireUserConfirmEvent confirm) {
            // 缓存待确认工具，等待前端审批
            List<ToolUseBlock> toolCalls = confirm.getToolCalls();
            hitlPendingStore.put(user.getUsername(), sessionId, toolCalls);
            List<ChatEvent.PendingToolCall> pendingList = toolCalls.stream()
                    .map(t -> ChatEvent.PendingToolCall.builder()
                            .toolCallId(t.getId())
                            .toolName(t.getName())
                            .toolInput(toJson(t.getInput()))
                            .build())
                    .toList();
            return builder.type("confirm_request").replyId(confirm.getReplyId())
                    .pendingToolCalls(pendingList).build();
        } else if (event instanceof SubagentExposedEvent sub) {
            return builder.type("subagent").subagentId(sub.getSubagentId())
                    .label(sub.getLabel()).build();
        } else if (event instanceof AgentEndEvent end) {
            // 注意：此处不能清理待确认缓存——HITL 暂停时回合以 end 事件结束，
            // end 先于用户审批到达，提前清理会导致 confirm 接口找不到待确认工具
            // （报「当前会话没有待确认的工具调用」）；
            // 被放弃的陈旧确认在新回合开始时统一作废（见 doChat）
            return builder.type("end").replyId(end.getReplyId()).build();
        }
        // 其它事件（思考/模型调用等）暂不透传，前端无需感知
        return builder.type("ignore").build();
    }

    /** 流式事件累积助手回复：text 增量追加，end 事件触发整轮落库 */
    private void accumulateAndFlush(LoginUser user, String sessionId, ChatEvent chatEvent, StringBuilder replyBuffer) {
        if ("text".equals(chatEvent.getType()) && chatEvent.getDelta() != null) {
            replyBuffer.append(chatEvent.getDelta());
        } else if ("end".equals(chatEvent.getType())) {
            persistMessage(user, sessionId, ChatMessage.ROLE_ASSISTANT,
                    replyBuffer.toString(), null, ChatMessage.STATUS_OK);
            // 对话回合结束后异步生成/更新会话摘要（跨会话记忆）
            sessionSummaryService.generateSummaryAsync(sessionId, user.getUsername());
        }
    }

    /**
     * Token 自动追踪（异步）：从 ModelCallEndEvent 提取 ChatUsage，异步写入 token_usage_log 表。
     * <p>
     * provider/modelName 由调用方从回合级缓存传入，避免每次模型调用都查库+解密 API Key。
     * DB 写入在 boundedElastic 线程池异步执行，不阻塞 SSE 事件流；失败仅告警不抛出。
     */
    private void recordTokenUsage(LoginUser user, String sessionId, ModelCallEndEvent event,
                                  String providerName, String modelName, List<String> toolNames) {
        ChatUsage usage = event.getUsage();
        if (usage == null || usage.getTotalTokens() <= 0) {
            return;
        }
        // 异步写入 DB：避免阻塞 SSE 事件流（每次模型调用节省 2-5ms 同步阻塞）
        Mono.fromRunnable(() -> {
            try {
                // 工具名称：多个工具用逗号分隔
                String toolNameStr = toolNames.isEmpty() ? null : String.join(",", toolNames);
                tokenUsageService.recordUsage(
                        user.getUserId(),
                        user.getTenantId(),
                        user.getUsername(),
                        sessionId,
                        providerName,
                        modelName,
                        usage.getInputTokens(),
                        usage.getOutputTokens(),
                        event.getReplyId(),
                        toolNameStr
                );
                log.debug("Token 已记录: user={}, input={}, output={}, total={}, tools={}",
                        user.getUsername(), usage.getInputTokens(), usage.getOutputTokens(), usage.getTotalTokens(), toolNameStr);
            } catch (Exception e) {
                log.warn("Token 追踪记录失败（不影响对话）: user={}, error={}", user.getUsername(), e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    /**
     * 聊天消息落库：业务线程构建快照，insert 移交 boundedElastic 异步执行。
     * 失败仅告警不抛出——历史持久化是辅助能力，绝不能阻断对话主流程。
     */
    private void persistMessage(LoginUser user, String sessionId, String role,
                                String content, List<String> attachments, int status) {
        // 空内容不入库（如中断回合无任何产出），避免脏数据干扰历史回看
        if (!StringUtils.hasText(content)) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setTenantId(user.getTenantId());
        message.setSessionId(sessionId);
        message.setUsername(user.getUsername());
        message.setRole(role);
        message.setContent(content);
        if (attachments != null && !attachments.isEmpty()) {
            message.setAttachments(toJson(attachments));
        }
        message.setStatus(status);
        Mono.fromRunnable(() -> {
            // insert 在 boundedElastic 异步线程执行，该线程没有 UserContextHolder（ThreadLocal 不跨线程），
            // 审计填充器取不到操作人会导致 creator/updater 等审计字段全空；
            // 故在异步线程内桥接用户上下文，落库后立即清理，防线程池复用串号
            UserContextHolder.set(user);
            try {
                chatMessageMapper.insert(message);
            } catch (Exception e) {
                log.warn("聊天消息落库失败: session={}, role={}", sessionId, role, e);
            } finally {
                UserContextHolder.clear();
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    /**
     * 会话元数据落库（已存在则刷新活跃时间）。
     * <p>
     * 优化：使用 INSERT ... ON DUPLICATE KEY UPDATE 单次 SQL 替代 SELECT + INSERT/UPDATE，
     * 利用唯一索引 uk_user_session(username, session_id) 实现幂等写入。
     */
    private void ensureSession(LoginUser user, String sessionId, String firstMessage) {
        String title = firstMessage.length() > TITLE_MAX_LENGTH
                ? firstMessage.substring(0, TITLE_MAX_LENGTH) : firstMessage;
        chatSessionMapper.insertOrUpdate(user.getTenantId(), sessionId, user.getUsername(), title);
    }

    /** 构建 RuntimeContext（userId + sessionId 定位状态槽，同键自动串行化） */
    private RuntimeContext runtimeContext(LoginUser user, String sessionId) {
        RuntimeContext rc = RuntimeContext.builder()
                .userId(user.getUserId())
                .sessionId(sessionId)
                .build();
        // 将用户信息存入 RuntimeContext 附加属性，供工具在 reactor 线程中读取
        // （UserContextHolder 基于 ThreadLocal，在流式执行的工具调用线程上不可用）
        rc.put("userId", user.getUserId());
        rc.put("username", user.getUsername());
        rc.put("tenantId", user.getTenantId());
        return rc;
    }

    /** 按扩展名推断 MIME 类型 */
    private String guessMediaType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        } else if (lower.endsWith(".webp")) {
            return "image/webp";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    /** 工具入参序列化为 JSON 字符串（失败时兜底 toString） */
    private String toJson(Object input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            return String.valueOf(input);
        }
    }

    /**
     * 判断当前模型是否支持视觉（图片输入）。
     * <p>
     * 基于模型名称模式匹配：包含 vl/vision/gpt-4o/gemini 等关键词的视为视觉模型。
     * 未匹配到的保守视为不支持，由 API 侧返回的错误兜底。
     */
    private boolean supportsVision(ModelProviderConfig cfg) {
        if (cfg == null || cfg.getModelName() == null) return false;
        String model = cfg.getModelName().toLowerCase();
        // 通义千问 VL 系列、GPT-4o 系列、Gemini 系列、MiniMax-VL 等
        return model.contains("vl") || model.contains("vision")
                || model.contains("gpt-4o") || model.contains("gemini")
                || model.contains("minimax-vl");
    }
}
