package com.claw.agent.controller.system;

import com.claw.agent.common.OperType;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.model.UserChannel;
import com.claw.agent.service.UserChannelService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 用户渠道绑定控制器：管理用户与外部渠道（微信/Slack/Telegram 等）的绑定关系。
 * <p>
 * 支持单聊和群聊两种场景：
 * - 单聊：channelGroupId 为 NULL，会话归属于用户个人
 * - 群聊：channelGroupId 有值，会话归属于群组，群内成员共享上下文
 * <p>
 * 所有接口需登录；操作按 userId 隔离，Service 层校验归属。
 */
@Slf4j
@Tag(name = "渠道管理", description = "用户与外部渠道绑定管理")
@RestController
@RequestMapping("/api/channel")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserChannelController {

    /** 操作日志模块名 */
    private static final String MODULE = "渠道管理";

    private final UserChannelService userChannelService;

    /** 当前用户的渠道绑定列表（按创建时间倒序） */
    @Operation(summary = "渠道绑定列表", description = "当前用户的渠道绑定列表")
    @GetMapping("/list")
    public Mono<Result<List<UserChannel>>> list() {
        return ReactiveSupport.call(userChannelService::listByUser);
    }

    /** 新增渠道绑定 */
    @Operation(summary = "新增渠道绑定", description = "新增用户与外部渠道的绑定")
    @PostMapping
    public Mono<Result<Void>> add(@RequestBody UserChannel channel) {
        return ReactiveSupport.run(MODULE, OperType.CREATE, "新增渠道绑定",
                u -> userChannelService.addChannel(u, channel));
    }

    /** 更新渠道绑定（显示名、群组名、token 等） */
    @Operation(summary = "更新渠道绑定", description = "更新渠道绑定信息（显示名/群组名/token）")
    @PutMapping("/{id}")
    public Mono<Result<Void>> update(@PathVariable Long id, @RequestBody UserChannel channel) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "更新渠道绑定",
                u -> userChannelService.updateChannel(u, id, channel));
    }

    /** 删除渠道绑定（解绑渠道） */
    @Operation(summary = "删除渠道绑定", description = "解绑指定渠道")
    @DeleteMapping("/{id}")
    public Mono<Result<Void>> delete(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.DELETE, "删除渠道绑定",
                u -> userChannelService.deleteChannel(u, id));
    }

    /** 同步群组成员（从渠道 API 拉取最新成员列表） */
    @Operation(summary = "同步群组成员", description = "从渠道 API 拉取最新群成员列表")
    @PostMapping("/{id}/sync-members")
    public Mono<Result<Void>> syncMembers(@PathVariable Long id) {
        return ReactiveSupport.run(MODULE, OperType.UPDATE, "同步群组成员",
                u -> userChannelService.syncGroupMembers(u, id));
    }
}
