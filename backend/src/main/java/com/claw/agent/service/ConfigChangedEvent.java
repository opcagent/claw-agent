package com.claw.agent.service;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 配置变更事件：数据库配置（模型提供商 / 运行参数）保存成功后发布。
 * <p>
 * AgentRegistry 监听本事件，按作用域失效并热重建受影响用户的 Agent：
 * GLOBAL 变更 → 失效全部；TENANT 变更 → 失效该租户全部用户；
 * USER 变更 → 按 {@link #ownerId} 精准失效单个用户。
 */
@Getter
public class ConfigChangedEvent extends ApplicationEvent {

    /** 变更的作用域：GLOBAL / TENANT / USER */
    private final String scope;

    /** 变更的租户ID（GLOBAL 时为 0） */
    private final Long tenantId;

    /** 变更的归属用户ID（仅 USER 作用域有值，用于精准失效） */
    private final String ownerId;

    public ConfigChangedEvent(Object source, String scope, Long tenantId, String ownerId) {
        super(source);
        this.scope = scope;
        this.tenantId = tenantId;
        this.ownerId = ownerId;
    }
}
