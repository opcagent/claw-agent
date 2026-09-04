package com.claw.agent.config.infra;

import com.claw.agent.config.agent.AgentRegistry;
import com.claw.agent.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Map;

/**
 * Agent 缓存失效器：封装 AgentRegistry 的缓存失效逻辑，供本地事件和 Redis Pub/Sub 共同调用。
 * <p>
 * 多实例部署时，配置变更通过 Redis Pub/Sub 广播到所有节点，
 * 每个节点的 PubSubMessageListener 调用本组件失效本地 Agent 缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentCacheInvalidator {

    private final AgentRegistry agentRegistry;

    /**
     * 按作用域失效 Agent 缓存（与 AgentRegistry.onConfigChanged 逻辑一致）。
     *
     * @param scope   作用域：GLOBAL / TENANT / USER
     * @param tenantId 租户 ID
     * @param ownerId 归属用户 ID
     */
    public void invalidate(String scope, Long tenantId, String ownerId) {
        if (scope == null) return;
        if (ConfigService.SCOPE_PLATFORM.equals(scope)) {
            agentRegistry.invalidateAll();
            log.info("[Pub/Sub] 全局配置变更，已清空全部 Agent 缓存");
            return;
        }
        if (ConfigService.SCOPE_TENANT.equals(scope)) {
            agentRegistry.invalidateByTenant(tenantId);
            log.info("[Pub/Sub] 租户 {} 配置变更，已失效该租户 Agent 缓存", tenantId);
            return;
        }
        if (ConfigService.SCOPE_USER.equals(scope) && StringUtils.hasText(ownerId)) {
            agentRegistry.invalidate(ownerId);
            log.info("[Pub/Sub] 用户 {} 配置变更，已失效 Agent 缓存", ownerId);
        }
    }
}
