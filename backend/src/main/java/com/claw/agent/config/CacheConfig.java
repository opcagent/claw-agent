package com.claw.agent.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.claw.agent.config.infra.CaffeineCacheManager;
import com.claw.agent.config.infra.RedisPubSub;
import com.claw.agent.model.DictData;
import com.claw.agent.model.Menu;
import com.claw.agent.model.ModelProviderConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存配置（Caffeine）。
 * <p>
 * 仅缓存「高频读取 + 低频变更」的数据，写入侧在对应 service 的增删改方法中
 * 手动调用 {@code cache.invalidate(...)} 保证一致性。
 * <p>
 * 多实例部署时，缓存失效通过 Redis Pub/Sub 广播到所有节点，
 * 各节点收到 {@link RedisPubSub#EVT_CACHE_INVALIDATE} 事件后失效本地 Caffeine 缓存。
 */
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final CaffeineCacheManager caffeineCacheManager;
    private final RedisPubSub redisPubSub;

    @PostConstruct
    public void registerCaches() {
        // 缓存注册延迟到 @PostConstruct，此时所有 Bean 已创建
    }

    /**
     * 配置单键值缓存（agent_config 表）。
     * <p>
     * 键格式：scope:tenantId:ownerId:configKey；值：配置字符串值（可为 null 占位）。
     * 每次 Agent 构建调用 6-8 次 resolveValue，每次内部查 3 个作用域，
     * 缓存后从 18-24 次 DB 查询降至 0 次（命中时）。
     */
    @Bean
    public Cache<String, String> configValueCache() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
        caffeineCacheManager.register("configValueCache", cache);
        return cache;
    }

    /**
     * 当前生效模型提供商缓存。
     * <p>
     * 键格式：scope:tenantId:ownerId；值：ModelProviderConfig（含加密 API Key）。
     * 每次对话前 2 次调用（AgentService + AgentRegistry），缓存后消除重复查库+解密。
     * <p>
     * 注意：缓存的是解密前的原始对象，调用方仍需 copy 后再解密，避免污染缓存。
     */
    @Bean
    public Cache<String, ModelProviderConfig> providerConfigCache() {
        Cache<String, ModelProviderConfig> cache = Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
        caffeineCacheManager.register("providerConfigCache", cache);
        return cache;
    }

    /**
     * 启用菜单列表缓存。
     * <p>
     * 菜单为平台级数据，每次页面加载都查询，变更频率极低（仅管理员改菜单时）。
     * 缓存后所有用户共享同一份菜单快照，写操作（增删改）时整体清空。
     */
    @Bean
    public Cache<String, List<Menu>> menuCache() {
        Cache<String, List<Menu>> cache = Caffeine.newBuilder()
                .maximumSize(5)
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .build();
        caffeineCacheManager.register("menuCache", cache);
        return cache;
    }

    /**
     * 字典数据缓存（按 租户+字典类型 缓存已启用的字典列表）。
     * <p>
     * 键格式：tenantId:dictType；值：合并后的字典数据列表。
     * 前端下拉/标签渲染高频调用，变更频率低。
     */
    @Bean
    public Cache<String, List<DictData>> dictDataCache() {
        Cache<String, List<DictData>> cache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
        caffeineCacheManager.register("dictDataCache", cache);
        return cache;
    }
}
