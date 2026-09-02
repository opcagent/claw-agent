package com.claw.agent.controller.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.common.Result;
import com.claw.agent.common.ResultCode;
import com.claw.agent.mapper.UserMapper;
import com.claw.agent.mapper.RoleMapper;
import com.claw.agent.model.Role;
import com.claw.agent.model.User;
import com.claw.agent.model.UserChannel;
import com.claw.agent.model.dto.ChatEvent;
import com.claw.agent.model.dto.ChatRequest;
import com.claw.agent.security.LoginUser;
import com.claw.agent.service.AgentService;
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

import java.util.List;
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
    private final AgentService agentService;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

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

            // 2. 验证请求签名（各适配器实现具体验证逻辑）
            if (!adapter.verifySignature(signature, timestamp, body)) {
                log.warn("Webhook 签名验证失败：channelType={}", channelType);
                return Result.<Void>fail(ResultCode.PARAM_ERROR, "签名验证失败");
            }

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
                // 投递到群组共享会话：取第一个绑定用户的 userId 作为会话归属
                UserChannel firstMember = members.get(0);
                deliverToGroupSession(adapter, firstMember, content, groupId);
                log.info("群聊消息已路由：groupId={}, members={}, content={}", groupId, members.size(), content);
            } else if (senderId != null && !senderId.isEmpty()) {
                // 单聊消息
                UserChannel binding = userChannelService.findByChannelUser(channelType, senderId);
                if (binding == null) {
                    log.warn("单聊消息无绑定用户：channelType={}, senderId={}", channelType, senderId);
                    return Result.<Void>fail(ResultCode.PARAM_ERROR, "该用户未绑定平台账号");
                }
                // 投递到用户个人会话
                deliverToUserSession(adapter, binding, content);
                log.info("单聊消息已路由：userId={}, content={}", binding.getUserId(), content);
            } else {
                log.warn("Webhook 消息缺少 sender_id 或 group_id：{}", body);
                return Result.<Void>fail(ResultCode.PARAM_ERROR, "消息格式不正确");
            }

            return Result.<Void>ok();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 投递消息到用户个人会话：构建 LoginUser → 调用 AgentService → 收集回复 → 通过渠道发回。
     * <p>
     * 异步执行：Webhook 先返回 200 确认收到，Agent 回复通过渠道适配器异步发送。
     *
     * @param adapter 渠道适配器（用于发送回复）
     * @param binding 渠道绑定记录（含 userId、accessToken）
     * @param content 用户消息内容
     */
    private void deliverToUserSession(ChannelAdapter adapter, UserChannel binding, String content) {
        LoginUser loginUser = buildLoginUser(binding.getUserId());
        if (loginUser == null) {
            log.warn("渠道绑定的用户不存在：userId={}", binding.getUserId());
            return;
        }
        ChatRequest request = new ChatRequest();
        request.setContent(content);
        // 异步调用 Agent 并收集回复，然后通过渠道发回
        agentService.chat(loginUser, request)
                .collectList()
                .subscribe(events -> {
                    String reply = collectReplyText(events);
                    if (reply != null && !reply.isEmpty()) {
                        try {
                            adapter.sendMessage(binding.getChannelUserId(), reply, binding.getAccessToken());
                            log.debug("已通过渠道回复：channelType={}, userId={}", binding.getChannelType(), binding.getChannelUserId());
                        } catch (Exception e) {
                            log.warn("渠道回复失败：channelType={}, userId={}, error={}", binding.getChannelType(), binding.getChannelUserId(), e.getMessage());
                        }
                    }
                }, error -> log.warn("Agent 对话执行失败：userId={}, error={}", binding.getUserId(), error.getMessage()));
    }

    /**
     * 投递消息到群组共享会话：逻辑与单聊类似，但使用 sendGroupMessage 发送到群。
     *
     * @param adapter 渠道适配器
     * @param member  群成员绑定记录（取第一个作为会话归属）
     * @param content 用户消息内容
     * @param groupId 群组 ID
     */
    private void deliverToGroupSession(ChannelAdapter adapter, UserChannel member, String content, String groupId) {
        LoginUser loginUser = buildLoginUser(member.getUserId());
        if (loginUser == null) {
            log.warn("群成员绑定的用户不存在：userId={}", member.getUserId());
            return;
        }
        ChatRequest request = new ChatRequest();
        request.setContent(content);
        agentService.chat(loginUser, request)
                .collectList()
                .subscribe(events -> {
                    String reply = collectReplyText(events);
                    if (reply != null && !reply.isEmpty()) {
                        try {
                            adapter.sendGroupMessage(groupId, reply, member.getAccessToken());
                            log.debug("已通过渠道回复群组：groupId={}", groupId);
                        } catch (Exception e) {
                            log.warn("群组回复失败：groupId={}, error={}", groupId, e.getMessage());
                        }
                    }
                }, error -> log.warn("群组 Agent 对话执行失败：groupId={}, error={}", groupId, error.getMessage()));
    }

    /**
     * 根据 userId 构建 LoginUser：从数据库加载用户信息和角色。
     * <p>
     * Webhook 场景没有 JWT，需要手动构建登录用户上下文。
     *
     * @param userId 用户 ID
     * @return LoginUser（用户不存在返回 null）
     */
    private LoginUser buildLoginUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        List<Role> roles = roleMapper.selectRolesByUserId(userId);
        List<String> roleKeys = roles.stream().map(Role::getRoleKey).toList();
        return new LoginUser(user.getId(), user.getUsername(), user.getTenantId(), roleKeys);
    }

    /**
     * 从 ChatEvent 列表中提取助手回复文本：收集所有 text 类型事件的 delta。
     *
     * @param events Agent 对话事件列表
     * @return 拼接后的回复文本
     */
    private String collectReplyText(List<ChatEvent> events) {
        StringBuilder sb = new StringBuilder();
        for (ChatEvent event : events) {
            if ("text".equals(event.getType()) && event.getDelta() != null) {
                sb.append(event.getDelta());
            }
        }
        return sb.toString();
    }
}
