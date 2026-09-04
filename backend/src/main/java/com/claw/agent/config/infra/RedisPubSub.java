package com.claw.agent.config.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 消息总线：多实例部署时跨节点广播配置变更与缓存失效事件。
 * <p>
 * 单机模式（Redis 不可用）时所有方法静默降级为 no-op，不影响功能正确性。
 * <p>
 * 消息格式：JSON {@link PubSubMessage}，包含事件类型与负载数据。
 * 频道：{@value #CHANNEL}，所有实例订阅同一频道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPubSub {

    /** Redis Pub/Sub 频道名 */
    public static final String CHANNEL = "claw-agent:pubsub";

    /** 事件类型：配置变更（触发 Agent 缓存失效） */
    public static final String EVT_CONFIG_CHANGED = "config_changed";

    /** 事件类型：Caffeine 缓存失效 */
    public static final String EVT_CACHE_INVALIDATE = "cache_invalidate";

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private RedisMessageListenerContainer messageListenerContainer;

    /**
     * Redis 是否可用。
     */
    public boolean isAvailable() {
        return redisTemplate != null;
    }

    /**
     * 发布配置变更事件（通知所有节点失效 Agent 缓存）。
     *
     * @param scope   作用域：GLOBAL / TENANT / USER
     * @param tenantId 租户 ID
     * @param ownerId 归属用户 ID（仅 USER 作用域）
     */
    public void publishConfigChanged(String scope, Long tenantId, String ownerId) {
        if (!isAvailable()) return;
        try {
            PubSubMessage msg = new PubSubMessage();
            msg.setEventType(EVT_CONFIG_CHANGED);
            msg.setScope(scope);
            msg.setTenantId(tenantId);
            msg.setOwnerId(ownerId);
            String json = objectMapper.writeValueAsString(msg);
            redisTemplate.convertAndSend(CHANNEL, json);
            log.debug("已发布配置变更事件: scope={}, tenantId={}, ownerId={}", scope, tenantId, ownerId);
        } catch (JsonProcessingException e) {
            log.warn("序列化 Pub/Sub 消息失败", e);
        }
    }

    /**
     * 发布缓存失效事件（通知所有节点失效指定 Caffeine 缓存）。
     *
     * @param cacheName 缓存名称（如 "menuCache" / "dictDataCache"）
     */
    public void publishCacheInvalidate(String cacheName) {
        if (!isAvailable()) return;
        try {
            PubSubMessage msg = new PubSubMessage();
            msg.setEventType(EVT_CACHE_INVALIDATE);
            msg.setCacheName(cacheName);
            String json = objectMapper.writeValueAsString(msg);
            redisTemplate.convertAndSend(CHANNEL, json);
            log.debug("已发布缓存失效事件: cacheName={}", cacheName);
        } catch (JsonProcessingException e) {
            log.warn("序列化 Pub/Sub 消息失败", e);
        }
    }

    /**
     * 订阅 Pub/Sub 频道，注册消息处理器。
     *
     * @param listener 消息监听器
     */
    public void subscribe(MessageListener listener) {
        if (messageListenerContainer == null) {
            log.info("Redis Pub/Sub 不可用（RedisMessageListenerContainer 未初始化），跳过订阅");
            return;
        }
        messageListenerContainer.addMessageListener(listener, new ChannelTopic(CHANNEL));
        log.info("已订阅 Redis Pub/Sub 频道: {}", CHANNEL);
    }

    /**
     * Pub/Sub 消息体。
     */
    @Data
    public static class PubSubMessage {
        /** 事件类型 */
        private String eventType;
        /** 作用域（config_changed 事件） */
        private String scope;
        /** 租户 ID（config_changed 事件） */
        private Long tenantId;
        /** 归属用户 ID（config_changed 事件，USER 作用域） */
        private String ownerId;
        /** 缓存名称（cache_invalidate 事件） */
        private String cacheName;
    }
}
