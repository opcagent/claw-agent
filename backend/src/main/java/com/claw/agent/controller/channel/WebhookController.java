package com.claw.agent.controller.channel;

import com.claw.agent.common.Result;
import com.claw.agent.common.ResultCode;
import com.claw.agent.model.UserChannel;
import com.claw.agent.service.UserChannelService;
import com.claw.agent.service.channel.ChannelAdapter;
import com.claw.agent.service.channel.ChannelAdapterRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * 渠道 Webhook 控制器：接收外部渠道推送的消息与事件回调。
 * <p>
 * 该接口不需要登录认证（由渠道平台调用），但需验证请求签名（由各适配器内部实现）。
 * <p>
 * 消息路由逻辑：
 * - 单聊消息：根据 channelType + channelUserId 查找绑定用户，投递到该用户的 Agent 会话
 * - 群聊消息：根据 channelType + groupId 查找群成员，投递到共享的群组会话
 */
@Slf4j
@Tag(name = "渠道 Webhook", description = "外部渠道消息回调入口")
@RestController
@RequestMapping("/api/webhook/channel")
@RequiredArgsConstructor
public class WebhookController {

    private final ChannelAdapterRegistry adapterRegistry;
    private final UserChannelService userChannelService;

    /**
     * 接收渠道消息推送。
     * <p>
     * 请求体格式由各渠道适配器定义（通常为 JSON），这里用 Map 接收后交给适配器解析。
     *
     * @param channelType 渠道类型（路径参数）
     * @param signature   请求签名（查询参数，用于验证消息来源合法性）
     * @param timestamp   时间戳（查询参数，防重放攻击）
     * @param body        消息体（JSON）
     * @return 处理结果
     */
    @Operation(summary = "接收渠道消息", description = "接收外部渠道推送的消息与事件回调")
    @PostMapping("/{channelType}")
    public Mono<Result<Void>> receiveMessage(
            @PathVariable String channelType,
            @RequestParam(required = false) String signature,
            @RequestParam(required = false) String timestamp,
            @RequestBody Map<String, Object> body) {

        return Mono.fromCallable(() -> {
            // 1. 查找渠道适配器
            if (!adapterRegistry.hasAdapter(channelType)) {
                log.warn("收到未注册渠道类型的 Webhook：{}", channelType);
                return Result.<Void>fail(ResultCode.PARAM_ERROR, "不支持的渠道类型：" + channelType);
            }
            ChannelAdapter adapter = adapterRegistry.getAdapter(channelType);

            // 2. TODO: 验证请求签名（各适配器实现具体验证逻辑）
            // adapter.verifySignature(signature, timestamp, body);

            // 3. 解析消息（由各适配器负责解析渠道特定格式）
            log.info("收到 {} 渠道 Webhook 消息：{}", channelType, body);

            // 4. 路由消息到 Agent
            // 单聊：根据 sender_id 查找绑定用户 → 投递到用户会话
            // 群聊：根据 group_id 查找群成员 → 投递到群组共享会话
            String senderId = (String) body.get("sender_id");
            String groupId = (String) body.get("group_id");
            String content = (String) body.get("content");

            if (groupId != null && !groupId.isEmpty()) {
                // 群聊消息
                var members = userChannelService.findGroupMembers(channelType, groupId);
                if (members.isEmpty()) {
                    log.warn("群聊消息无绑定用户：channelType={}, groupId={}", channelType, groupId);
                    return Result.<Void>fail(ResultCode.PARAM_ERROR, "该群组未绑定任何用户");
                }
                // TODO: 投递到群组共享会话（取第一个绑定用户的 userId 作为会话归属）
                log.info("群聊消息路由：groupId={}, members={}, content={}", groupId, members.size(), content);
            } else if (senderId != null && !senderId.isEmpty()) {
                // 单聊消息
                UserChannel binding = userChannelService.findByChannelUser(channelType, senderId);
                if (binding == null) {
                    log.warn("单聊消息无绑定用户：channelType={}, senderId={}", channelType, senderId);
                    return Result.<Void>fail(ResultCode.PARAM_ERROR, "该用户未绑定平台账号");
                }
                // TODO: 投递到用户个人会话
                log.info("单聊消息路由：userId={}, content={}", binding.getUserId(), content);
            } else {
                log.warn("Webhook 消息缺少 sender_id 或 group_id：{}", body);
                return Result.<Void>fail(ResultCode.PARAM_ERROR, "消息格式不正确");
            }

            return Result.<Void>ok();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
