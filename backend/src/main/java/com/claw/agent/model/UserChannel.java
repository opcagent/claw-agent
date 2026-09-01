package com.claw.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户渠道绑定实体（对应数据库表 sys_user_channel）。
 * <p>
 * 支持单聊和群聊两种场景：
 * - 单聊：channelGroupId 为 NULL，会话归属于用户个人
 * - 群聊：channelGroupId 有值，会话归属于群组，群内成员共享上下文
 * <p>
 * OAuth token 加密存储（access_token / refresh_token），
 * 由渠道适配器负责 token 刷新逻辑。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user_channel")
public class UserChannel extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台用户 ID（关联 sys_user.id） */
    private String userId;

    /** 渠道类型：wechat / slack / telegram / web */
    private String channelType;

    /** 渠道侧用户标识（如微信 openid、Slack user_id） */
    private String channelUserId;

    /** 渠道侧显示名（如微信昵称） */
    private String channelUsername;

    /** 群组 ID（单聊为 NULL，群聊必填） */
    private String channelGroupId;

    /** 群组名称（如微信群名、Slack channel 名） */
    private String channelGroupName;

    /** 群内角色：owner / admin / member */
    private String groupRole;

    /** OAuth access_token（加密存储） */
    private String accessToken;

    /** OAuth refresh_token（加密存储） */
    private String refreshToken;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;
}
