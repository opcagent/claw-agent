package com.claw.agent.controller.chat;

import com.claw.agent.common.BizException;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.common.ResultCode;
import com.claw.agent.config.infra.GracefulShutdownManager;
import com.claw.agent.config.infra.RateLimiter;
import com.claw.agent.model.ChatMessage;
import com.claw.agent.model.ChatSession;
import com.claw.agent.model.dto.ChatEvent;
import com.claw.agent.model.dto.ChatRequest;
import com.claw.agent.model.dto.ConfirmRequest;
import com.claw.agent.model.dto.RenameSessionRequest;
import com.claw.agent.security.LoginUser;
import com.claw.agent.security.SecurityUtil;
import com.claw.agent.service.AgentService;
import com.claw.agent.service.ConfigService;
import com.claw.agent.service.TokenUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 聊天控制器：流式对话（SSE）、HITL 确认、会话管理。
 * <p>
 * 方法级鉴权：任何已登录用户可用（数据隔离由 AgentService 按 userId 完成）；
 * SSE 采用 POST + Authorization 头（前端用 fetch + ReadableStream 消费，
 * 以支持自定义请求头；EventSource 无法携带 Authorization）。
 */
@Slf4j
@Tag(name = "智能对话", description = "SSE 流式对话/会话管理/确认回复")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatController {

    private final AgentService agentService;
    private final GracefulShutdownManager shutdownManager;
    private final RateLimiter rateLimiter;
    private final ConfigService configService;
    private final TokenUsageService tokenUsageService;

    /** 发起流式对话（SSE）；sessionId 为空时服务端新建会话 */
    @Operation(summary = "流式对话", description = "发起 SSE 流式对话，sessionId 为空时服务端新建会话")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> stream(@RequestBody ChatRequest request) {
        // 优雅停机：停机中拒绝新 SSE 请求，前端收到 503 提示用户稍后重试
        if (shutdownManager.isShuttingDown()) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE);
        }
        // 全局并发保护：超出上限时快速拒绝，避免排队等待占用连接
        if (!rateLimiter.tryAcquireGlobal()) {
            throw new BizException(ResultCode.RATE_LIMITED, "系统繁忙，当前并发请求过多，请稍后再试");
        }
        // acquire 必须在 doOnSubscribe 内调用（订阅期），与 doFinally 的 release 配对；
        // 若放在方法体（装配期），Flux 装配后未订阅时 activeStreams 只增不减 → 泄漏
        return SecurityUtil.currentUser()
                .flatMapMany(user -> {
                    // 用户级限流：滑动窗口内请求过多，返回空流由 switchIfEmpty 转错误
                    if (!rateLimiter.tryAcquireUser(user.getUsername())) {
                        return Flux.<ChatEvent>empty();
                    }
                    // Token 配额告警（非阻断）：达到阈值时在 SSE 流头部插入告警事件，对话正常继续
                    Flux<ChatEvent> quotaWarnEvent = buildQuotaWarnEvent(user);
                    Flux<ChatEvent> chatEvents = agentService.chat(user, request);
                    return quotaWarnEvent != null
                            ? Flux.concat(quotaWarnEvent, chatEvents)
                            : chatEvents;
                })
                // 用户限流返回空流时，统一转为限流错误（doFinally 负责释放 acquire + global）
                .switchIfEmpty(Flux.error(new BizException(ResultCode.RATE_LIMITED,
                        "请求过于频繁，请稍后重试")))
                .map(event -> ServerSentEvent.<ChatEvent>builder()
                        .event(event.getType())
                        .data(event)
                        .build())
                .doOnSubscribe(sub -> shutdownManager.acquire())
                .doFinally(signal -> {
                    shutdownManager.release();
                    rateLimiter.releaseGlobal();
                });
    }

    /** HITL 确认：允许/拒绝待确认的工具调用，恢复执行（SSE） */
    @Operation(summary = "HITL 确认", description = "允许/拒绝待确认的工具调用，恢复 Agent 执行（SSE）")
    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> confirm(@RequestBody ConfirmRequest request) {
        if (shutdownManager.isShuttingDown()) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE);
        }
        if (!rateLimiter.tryAcquireGlobal()) {
            throw new BizException(ResultCode.RATE_LIMITED, "系统繁忙，请稍后再试");
        }
        return SecurityUtil.currentUser()
                .flatMapMany(user -> {
                    if (!rateLimiter.tryAcquireUser(user.getUsername())) {
                        return Flux.<ChatEvent>empty();
                    }
                    return agentService.confirm(user, request.getSessionId(),
                            Boolean.TRUE.equals(request.getApproved()));
                })
                .switchIfEmpty(Flux.error(new BizException(ResultCode.RATE_LIMITED)))
                .map(event -> ServerSentEvent.<ChatEvent>builder()
                        .event(event.getType())
                        .data(event)
                        .build())
                .doOnSubscribe(sub -> shutdownManager.acquire())
                .doFinally(signal -> {
                    shutdownManager.release();
                    rateLimiter.releaseGlobal();
                });
    }

    /** 当前用户的会话列表（按活跃时间倒序，默认只返回活跃会话） */
    @Operation(summary = "会话列表", description = "获取当前用户的会话列表，按活跃时间倒序；archived=true 返回已归档会话")
    @GetMapping("/sessions")
    public Mono<Result<List<ChatSession>>> sessions(
            @RequestParam(defaultValue = "false") boolean archived) {
        return ReactiveSupport.call(user -> agentService.listSessions(user, archived));
    }

    /** 按关键词搜索会话（在聊天消息中模糊匹配，返回命中的会话列表） */
    @Operation(summary = "搜索会话", description = "按关键词搜索聊天消息，返回匹配的会话列表")
    @GetMapping("/sessions/search")
    public Mono<Result<List<ChatSession>>> searchSessions(@RequestParam String keyword) {
        return ReactiveSupport.call(user -> agentService.searchSessions(user, keyword));
    }

    /** 指定会话的聊天记录（仅本人会话，时间正序；归属校验在 Service 层） */
    @Operation(summary = "聊天记录", description = "获取指定会话的聊天记录，时间正序")
    @GetMapping("/sessions/{sessionId}/messages")
    public Mono<Result<List<ChatMessage>>> messages(@PathVariable String sessionId) {
        return ReactiveSupport.call(user -> agentService.listMessages(user, sessionId));
    }

    /** 重新生成：删除最后一轮助手回复，重新发送最后一条用户消息（SSE） */
    @Operation(summary = "重新生成", description = "删除最后一轮助手回复，重新发送最后一条用户消息")
    @PostMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> regenerate(@RequestParam String sessionId) {
        if (shutdownManager.isShuttingDown()) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE);
        }
        if (!rateLimiter.tryAcquireGlobal()) {
            throw new BizException(ResultCode.RATE_LIMITED, "系统繁忙，请稍后再试");
        }
        return SecurityUtil.currentUser()
                .flatMapMany(user -> {
                    if (!rateLimiter.tryAcquireUser(user.getUsername())) {
                        return Flux.<ChatEvent>empty();
                    }
                    return agentService.regenerate(user, sessionId);
                })
                .switchIfEmpty(Flux.error(new BizException(ResultCode.RATE_LIMITED)))
                .map(event -> ServerSentEvent.<ChatEvent>builder()
                        .event(event.getType())
                        .data(event)
                        .build())
                .doOnSubscribe(sub -> shutdownManager.acquire())
                .doFinally(signal -> {
                    shutdownManager.release();
                    rateLimiter.releaseGlobal();
                });
    }

    /** 删除会话（元数据 + 聊天记录级联清理） */
    @Operation(summary = "删除会话", description = "删除会话，元数据与聊天记录级联清理")
    @DeleteMapping("/sessions/{sessionId}")
    public Mono<Result<Void>> deleteSession(@PathVariable String sessionId) {
        return ReactiveSupport.run(user -> agentService.deleteSession(user, sessionId));
    }

    /** 归档会话（隐藏但保留，可在「归档」Tab 查看/恢复/删除） */
    @Operation(summary = "归档会话", description = "将会话标记为已归档，从主列表隐藏")
    @PutMapping("/sessions/{sessionId}/archive")
    public Mono<Result<Void>> archiveSession(@PathVariable String sessionId) {
        return ReactiveSupport.run(user -> agentService.archiveSession(user, sessionId));
    }

    /** 取消归档（将已归档会话恢复为活跃状态） */
    @Operation(summary = "取消归档", description = "将已归档会话恢复为活跃状态")
    @PutMapping("/sessions/{sessionId}/unarchive")
    public Mono<Result<Void>> unarchiveSession(@PathVariable String sessionId) {
        return ReactiveSupport.run(user -> agentService.unarchiveSession(user, sessionId));
    }

    /** 修改会话标题 */
    @Operation(summary = "修改会话标题", description = "重命名指定会话的标题")
    @PutMapping("/sessions/{sessionId}/title")
    public Mono<Result<Void>> renameSession(@PathVariable String sessionId,
                                             @RequestBody RenameSessionRequest request) {
        return ReactiveSupport.run(user -> agentService.renameSession(user, sessionId, request.getTitle()));
    }

    /**
     * 导出会话为 Markdown 文件（文件下载接口，不走 Result 包装）。
     * <p>
     * 返回 Content-Disposition: attachment 的 .md 文件，前端触发浏览器下载。
     */
    @Operation(summary = "导出会话", description = "导出会话为 Markdown 文件下载")
    @GetMapping("/sessions/{sessionId}/export")
    public Mono<ResponseEntity<byte[]>> exportSession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "markdown") String format) {
        return SecurityUtil.currentUser()
                .flatMap(user -> Mono.fromCallable(() -> {
                    String md = agentService.exportAsMarkdown(user, sessionId);
                    byte[] bytes = md.getBytes(StandardCharsets.UTF_8);
                    // 文件名含中文时需 URL 编码，否则 Content-Disposition 解析乱码
                    String fileName = "chat-" + sessionId + ".md";
                    String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
                            .contentType(MediaType.parseMediaType("text/markdown; charset=utf-8"))
                            .body(bytes);
                }).subscribeOn(Schedulers.boundedElastic()))
                .switchIfEmpty(Mono.error(new com.claw.agent.common.BizException(
                        com.claw.agent.common.ResultCode.UNAUTHORIZED, "未登录")));
    }

    // ==================== Token 配额告警（非阻断） ====================

    /**
     * 构建 Token 配额告警事件（流头部插入）。
     * <p>
     * 配额为 0 或未达阈值时返回 null，不影响 SSE 流；
     * 达到告警阈值时生成一条 {@code quota_warn} 事件，前端展示横幅提醒。
     *
     * @param user 当前登录用户
     * @return 包含单条告警事件的 Flux，或 null（无需告警）
     */
    private Flux<ChatEvent> buildQuotaWarnEvent(LoginUser user) {
        try {
            int quotaWan = configService.resolveInt(
                    ConfigService.KEY_TOKEN_MONTHLY_QUOTA, user.getTenantId(), user.getUserId(), 0);
            if (quotaWan <= 0) {
                return null;
            }
            int warnPercent = configService.resolveInt(
                    ConfigService.KEY_TOKEN_QUOTA_WARN_PERCENT, user.getTenantId(), user.getUserId(), 80);
            TokenUsageService.QuotaStatus status =
                    tokenUsageService.checkQuota(user.getUserId(), user.getTenantId(), quotaWan, warnPercent);
            if (status == null || !status.warn()) {
                return null;
            }
            log.debug("Token 配额告警: user={}, percent={}, exceeded={}",
                    user.getUsername(), status.percent(), status.exceeded());
            ChatEvent warnEvent = ChatEvent.builder()
                    .type("quota_warn")
                    .sessionId("")
                    .message(status.exceeded()
                            ? "本月 Token 用量已达 " + status.percent() + "%，超出配额上限"
                            : "本月 Token 用量已达 " + status.percent() + "%，请注意控制使用")
                    .build();
            return Flux.just(warnEvent);
        } catch (Exception e) {
            // 配额检查失败不影响对话主流程
            log.warn("Token 配额检查失败（不影响对话）: user={}", user.getUsername(), e);
            return null;
        }
    }
}
