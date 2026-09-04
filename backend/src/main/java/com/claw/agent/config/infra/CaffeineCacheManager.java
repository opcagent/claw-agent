package com.claw.agent.config.infra;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caffeine 本地缓存管理器：集中注册所有 Caffeine 缓存实例，支持按名称失效。
 * <p>
 * 多实例部署时，配置变更通过 Redis Pub/Sub 广播缓存失效事件，
 * 各节点收到后调用 {@link #invalidate(String)} 失效对应的本地 Caffeine 缓存。
 */
@Slf4j
@Component
public class CaffeineCacheManager {

    /** 缓存名称 → 缓存实例 */
    private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

    /**
     * 注册缓存实例（启动时由 CacheConfig 调用）。
     *
     * @param name  缓存名称（如 "menuCache"）
     * @param cache 缓存实例
     */
    public void register(String name, Cache<?, ?> cache) {
        caches.put(name, cache);
        log.debug("已注册 Caffeine 缓存: {}", name);
    }

    /**
     * 按名称失效指定缓存（Pub/Sub 缓存失效事件触发）。
     *
     * @param cacheName 缓存名称
     */
    public void invalidate(String cacheName) {
        if (cacheName == null) return;
        Cache<?, ?> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidateAll();
            log.info("[Pub/Sub] Caffeine 缓存已失效: {}", cacheName);
        } else {
            log.warn("[Pub/Sub] 未找到 Caffeine 缓存: {}", cacheName);
        }
    }

    /**
     * 失效所有缓存（全局配置变更时调用）。
     */
    public void invalidateAll() {
        caches.forEach((name, cache) -> {
            cache.invalidateAll();
            log.info("[Pub/Sub] Caffeine 缓存已失效: {}", name);
        });
    }
}
