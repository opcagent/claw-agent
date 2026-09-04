package com.claw.agent.config.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 消息监听器：接收其他节点发布的配置变更与缓存失效事件。
 * <p>
 * 启动时自动订阅 {@link RedisPubSub#CHANNEL} 频道，收到消息后：
 * <ul>
 *   <li>config_changed → 通知 AgentRegistry 失效对应 Agent 缓存</li>
 *   <li>cache_invalidate → 失效本地 Caffeine 缓存</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PubSubMessageListener implements MessageListener {

    private final RedisPubSub redisPubSub;
    private final ObjectMapper objectMapper;
    private final AgentCacheInvalidator agentCacheInvalidator;
    private final CaffeineCacheManager caffeineCacheManager;

    @PostConstruct
    public void init() {
        redisPubSub.subscribe(this);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            RedisPubSub.PubSubMessage msg = objectMapper.readValue(json, RedisPubSub.PubSubMessage.class);
            log.debug("收到 Pub/Sub 消息: eventType={}, payload={}", msg.getEventType(), json);

            switch (msg.getEventType()) {
                case RedisPubSub.EVT_CONFIG_CHANGED:
                    agentCacheInvalidator.invalidate(msg.getScope(), msg.getTenantId(), msg.getOwnerId());
                    break;
                case RedisPubSub.EVT_CACHE_INVALIDATE:
                    caffeineCacheManager.invalidate(msg.getCacheName());
                    break;
                default:
                    log.warn("未知 Pub/Sub 事件类型: {}", msg.getEventType());
            }
        } catch (Exception e) {
            log.warn("处理 Pub/Sub 消息失败", e);
        }
    }
}
