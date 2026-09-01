package com.claw.agent.service.channel;

/**
 * 渠道适配器接口：定义各外部渠道（微信/Slack/Telegram 等）的统一对接规范。
 * <p>
 * 每个渠道实现需负责：
 * - OAuth 授权流程（获取 access_token / refresh_token）
 * - 消息收发（发送 Agent 回复、接收渠道消息）
 * - 群组信息同步（拉取群成员列表等）
 * <p>
 * 具体实现类以 Spring Bean 形式注入，按 channelType 路由分发。
 */
public interface ChannelAdapter {

    /**
     * 返回该适配器支持的渠道类型标识（如 "wechat" / "slack" / "telegram"）。
     *
     * @return 渠道类型字符串，与 sys_user_channel.channel_type 对应
     */
    String getChannelType();

    /**
     * 构建 OAuth 授权 URL（用于引导用户跳转到渠道授权页面）。
     *
     * @param state 防 CSRF 的随机 state 参数
     * @return 授权页面 URL
     */
    String buildAuthUrl(String state);

    /**
     * 通过授权码换取 access_token 和 refresh_token。
     *
     * @param code 授权码
     * @return token 结果（包含 accessToken / refreshToken / expiresIn 等）
     */
    TokenResult exchangeToken(String code);

    /**
     * 刷新 access_token（token 过期时调用）。
     *
     * @param refreshToken 现有的 refresh_token
     * @return 新的 token 结果
     */
    TokenResult refreshToken(String refreshToken);

    /**
     * 发送消息到渠道用户（单聊场景）。
     *
     * @param channelUserId 渠道侧用户标识
     * @param content       消息内容（纯文本或 Markdown）
     * @param accessToken   有效的 access_token
     */
    void sendMessage(String channelUserId, String content, String accessToken);

    /**
     * 发送消息到渠道群组（群聊场景）。
     *
     * @param groupId     群组 ID
     * @param content     消息内容
     * @param accessToken 有效的 access_token
     */
    void sendGroupMessage(String groupId, String content, String accessToken);

    /**
     * 拉取群组成员列表（用于同步群组成员绑定）。
     *
     * @param groupId     群组 ID
     * @param accessToken 有效的 access_token
     * @return 群成员信息列表
     */
    java.util.List<GroupMember> fetchGroupMembers(String groupId, String accessToken);

    /**
     * OAuth token 结果。
     */
    record TokenResult(String accessToken, String refreshToken, long expiresIn) {
    }

    /**
     * 群成员信息。
     */
    record GroupMember(String channelUserId, String channelUsername, String groupRole) {
    }
}
