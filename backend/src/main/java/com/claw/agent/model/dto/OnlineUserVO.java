package com.claw.agent.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线用户监控视图对象（管理端监控页展示用）。
 * <p>
 * 由活跃快照（OnlineUserTracker）补全昵称 / 租户名后下发；
 * online 由服务端按最近活跃时间判定，避免前后端时钟偏差导致状态不一致。
 */
@Data
@Builder
public class OnlineUserVO {

    /** 用户 ID（旧版 token 可能为空） */
    private String userId;

    /** 登录用户名 */
    private String username;

    /** 昵称（账号已删除时为空） */
    private String nickname;

    /** 所属租户 ID */
    private Long tenantId;

    /** 所属租户名称（租户不存在时为空） */
    private String tenantName;

    /** 最近一次活跃时间（任一认证通过的请求都会刷新） */
    private LocalDateTime lastActiveTime;

    /** 最近一次访问的客户端 IP */
    private String lastIp;

    /** 是否在线（最近活跃时间在在线阈值内） */
    private Boolean online;
}
