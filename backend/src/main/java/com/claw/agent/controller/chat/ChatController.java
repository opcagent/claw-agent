package com.claw.agent.controller.chat;

import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.ChatMessage;
import com.claw.agent.model.ChatSession;
import com.claw.agent.model.dto.ChatEvent;
import com.claw.agent.model.dto.ChatRequest;
import com.claw.agent.model.dto.ConfirmRequest;
import com.claw.agent.model.dto.RenameSessionRequest;
import com.claw.agent.security.SecurityUtil;
import com.claw.agent.service.AgentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    /** 发起流式对话（SSE）；sessionId 为空时服务端新建会话 */
    @Operation(summary = "流式对话", description = "发起 SSE 流式对话，sessionId 为空时服务端新建会话")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> stream(@RequestBody ChatRequest request) {
        return SecurityUtil.currentUser()
                .flatMapMany(user -> agentService.chat(user, request))
                .map(event -> ServerSentEvent.<ChatEvent>builder()
                        .event(event.getType())
                        .data(event)
                        .build());
    }

    /** HITL 确认：允许/拒绝待确认的工具调用，恢复执行（SSE） */
    @Operation(summary = "HITL 确认", description = "允许/拒绝待确认的工具调用，恢复 Agent 执行（SSE）")
    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> confirm(@RequestBody ConfirmRequest request) {
        return SecurityUtil.currentUser()
                .flatMapMany(user -> agentService.confirm(user, request.getSessionId(),
                        Boolean.TRUE.equals(request.getApproved())))
                .map(event -> ServerSentEvent.<ChatEvent>builder()
                        .event(event.getType())
                        .data(event)
                        .build());
    }

    /** 当前用户的会话列表（按活跃时间倒序，默认只返回活跃会话） */
    @Operation(summary = "会话列表", description = "获取当前用户的会话列表，按活跃时间倒序；archived=true 返回已归档会话")
    @GetMapping("/sessions")
    public Mono<Result<List<ChatSession>>> sessions(
            @RequestParam(defaultValue = "false") boolean archived) {
        return ReactiveSupport.call(user -> agentService.listSessions(user, archived));
    }

    /** 指定会话的聊天记录（仅本人会话，时间正序；归属校验在 Service 层） */
    @Operation(summary = "聊天记录", description = "获取指定会话的聊天记录，时间正序")
    @GetMapping("/sessions/{sessionId}/messages")
    public Mono<Result<List<ChatMessage>>> messages(@PathVariable String sessionId) {
        return ReactiveSupport.call(user -> agentService.listMessages(user, sessionId));
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
}
