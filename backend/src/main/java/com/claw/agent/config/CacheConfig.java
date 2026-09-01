package com.claw.agent.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.claw.agent.model.DictData;
import com.claw.agent.model.Menu;
import com.claw.agent.model.ModelProviderConfig;
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
 * 与 Redis 互补：Redis 用于分布式会话状态，Caffeine 用于单机热数据缓存，
 * 多实例部署时配置变更通过 {@link com.claw.agent.service.ConfigChangedEvent}
 * 触发 Agent 热重建，间接保证各节点缓存最终一致。
 */
@Configuration
public class CacheConfig {

    /**
     * 配置单键值缓存（agent_config 表）。
     * <p>
     * 键格式：scope:tenantId:ownerId:configKey；值：配置字符串值（可为 null 占位）。
     * 每次 Agent 构建调用 6-8 次 resolveValue，每次内部查 3 个作用域，
     * 缓存后从 18-24 次 DB 查询降至 0 次（命中时）。
     */
    @Bean
    public Cache<String, String> configValueCache() {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
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
        return Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 启用菜单列表缓存。
     * <p>
     * 菜单为平台级数据，每次页面加载都查询，变更频率极低（仅管理员改菜单时）。
     * 缓存后所有用户共享同一份菜单快照，写操作（增删改）时整体清空。
     */
    @Bean
    public Cache<String, List<Menu>> menuCache() {
        return Caffeine.newBuilder()
                .maximumSize(5)
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 字典数据缓存（按 租户+字典类型 缓存已启用的字典列表）。
     * <p>
     * 键格式：tenantId:dictType；值：合并后的字典数据列表。
     * 前端下拉/标签渲染高频调用，变更频率低。
     */
    @Bean
    public Cache<String, List<DictData>> dictDataCache() {
        return Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
    }
}
