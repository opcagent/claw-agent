package com.claw.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.claw.agent.model.UserChannel;
import com.claw.agent.security.LoginUser;

import java.util.List;

/**
 * 用户渠道绑定服务接口。
 * <p>
 * 支持单聊和群聊两种场景：
 * - 单聊：channelGroupId 为 NULL，会话归属于用户个人
 * - 群聊：channelGroupId 有值，会话归属于群组，群内成员共享上下文
 */
public interface UserChannelService extends IService<UserChannel> {

    /**
     * 查询当前用户的渠道绑定列表。
     *
     * @param current 当前登录用户
     * @return 渠道绑定列表
     */
    List<UserChannel> listByUser(LoginUser current);

    /**
     * 新增渠道绑定（OAuth 授权后调用）。
     *
     * @param current 当前登录用户
     * @param channel 渠道绑定信息
     */
    void addChannel(LoginUser current, UserChannel channel);

    /**
     * 更新渠道绑定（如 token 刷新、群组名称变更）。
     *
     * @param current 当前登录用户
     * @param id      绑定记录 ID
     * @param channel 更新内容
     */
    void updateChannel(LoginUser current, Long id, UserChannel channel);

    /**
     * 删除渠道绑定（解绑渠道）。
     *
     * @param current 当前登录用户
     * @param id      绑定记录 ID
     */
    void deleteChannel(LoginUser current, Long id);

    /**
     * 根据渠道类型和渠道侧用户 ID 查询绑定记录（Webhook 回调时用）。
     *
     * @param channelType   渠道类型
     * @param channelUserId 渠道侧用户 ID
     * @return 绑定记录（可能为 null）
     */
    UserChannel findByChannelUser(String channelType, String channelUserId);

    /**
     * 查询群组内所有成员的绑定记录（群聊消息路由时用）。
     *
     * @param channelType 渠道类型
     * @param groupId     群组 ID
     * @return 群成员绑定列表
     */
    List<UserChannel> findGroupMembers(String channelType, String groupId);

    /**
     * 同步群组成员列表（从渠道 API 拉取最新成员）。
     *
     * @param current   当前登录用户
     * @param channelId 渠道绑定 ID
     */
    void syncGroupMembers(LoginUser current, Long channelId);
}
