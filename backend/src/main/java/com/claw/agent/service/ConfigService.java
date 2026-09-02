package com.claw.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.common.BizException;
import com.claw.agent.common.CryptoUtil;
import com.claw.agent.common.ResultCode;
import com.claw.agent.mapper.AgentConfigMapper;
import com.claw.agent.mapper.ModelProviderConfigMapper;
import com.claw.agent.model.AgentConfigItem;
import com.claw.agent.model.ModelProviderConfig;
import com.claw.agent.model.dto.ParamKeyInfo;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 多租户三级作用域配置服务。
 * <p>
 * 所有 AgentScope 运行配置（模型提供商 / 运行参数）均落库管理，
 * 解析优先级：USER &gt; TENANT &gt; GLOBAL（就近覆盖）。
 * 配置变更后发布 {@link ConfigChangedEvent}，由 AgentRegistry 监听并热重建受影响用户的 Agent。
 * <p>
 * 阿里规约：业务逻辑收敛在 service 层，API Key 一律加密存储（enc: 前缀），禁止明文落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    /** 作用域：平台级（全局默认） */
    public static final String SCOPE_PLATFORM = "PLATFORM";
    
    /** 作用域：租户级 */
    public static final String SCOPE_TENANT = "TENANT";
    /** 作用域：用户级 */
    public static final String SCOPE_USER = "USER";

    /** 配置键：状态存储类型（redis / json） */
    public static final String KEY_STATE_STORE_TYPE = "state_store_type";
    /** 配置键：权限模式（DEFAULT / ACCEPT_EDITS / EXPLORE / BYPASS / DONT_ASK） */
    public static final String KEY_PERMISSION_MODE = "permission_mode";
    /** 配置键：上下文压缩触发消息数 */
    public static final String KEY_COMPACTION_TRIGGER = "compaction_trigger_messages";
    /** 配置键：上下文压缩保留消息数 */
    public static final String KEY_COMPACTION_KEEP = "compaction_keep_messages";
    /** 配置键：记忆刷新节流间隔（分钟） */
    public static final String KEY_MEMORY_FLUSH_MINUTES = "memory_flush_throttle_minutes";

    // 搜索引擎 API Key 配置键（存 agent_config 表，三级作用域，加密存储）
    /** 配置键：Tavily API Key */
    public static final String KEY_SEARCH_TAVILY_API_KEY = "search.tavily.api_key";
    /** 配置键：Brave API Key */
    public static final String KEY_SEARCH_BRAVE_API_KEY = "search.brave.api_key";
    /** 配置键：Bing API Key */
    public static final String KEY_SEARCH_BING_API_KEY = "search.bing.api_key";
    /** 配置键：SearXNG 实例地址 */
    public static final String KEY_SEARCH_SEARXNG_BASE_URL = "search.searxng.base_url";

    /** 配置键：百度 OCR API Key */
    public static final String KEY_BAIDU_OCR_API_KEY = "baidu.ocr.api_key";
    /** 配置键：百度 OCR Secret Key */
    public static final String KEY_BAIDU_OCR_SECRET_KEY = "baidu.ocr.secret_key";

    /** 配置键：腾讯云 OCR SecretId */
    public static final String KEY_TENCENT_OCR_SECRET_ID = "tencent.ocr.secret_id";
    /** 配置键：腾讯云 OCR SecretKey */
    public static final String KEY_TENCENT_OCR_SECRET_KEY = "tencent.ocr.secret_key";

    /** 搜索引擎配置键列表（用于前端展示和批量解析） */
    public static final List<String> SEARCH_CONFIG_KEYS = List.of(
            KEY_SEARCH_TAVILY_API_KEY, KEY_SEARCH_BRAVE_API_KEY,
            KEY_SEARCH_BING_API_KEY, KEY_SEARCH_SEARXNG_BASE_URL);

    /** 搜索引擎配置键 → 显示名称映射 */
    public static final Map<String, String> SEARCH_CONFIG_LABELS = Map.of(
            KEY_SEARCH_TAVILY_API_KEY, "Tavily API Key",
            KEY_SEARCH_BRAVE_API_KEY, "Brave API Key",
            KEY_SEARCH_BING_API_KEY, "Bing API Key",
            KEY_SEARCH_SEARXNG_BASE_URL, "SearXNG 实例地址");

    /**
     * 解密配置值：enc: 前缀的密文解密返回明文，非密文原样返回。
     * <p>
     * 供工具集（如 OcrTools）在运行时读取加密配置后解密使用。
     */
    public String decryptValue(String value) {
        return cryptoUtil.decrypt(value);
    }

    /**
     * 解析当前用户的搜索引擎 API Key（三级作用域：USER > TENANT > PLATFORM）。
     * <p>
     * API Key 在数据库中加密存储，返回解密后的明文。
     * 未配置时返回 null，调用方应回退到 application.yml 默认值。
     *
     * @param configKey  配置键（如 search.tavily.api_key）
     * @param tenantId   租户ID
     * @param userId   用户ID
     * @return 解密后的 API Key；未配置返回 null
     */
    public String resolveSearchApiKey(String configKey, Long tenantId, String userId) {
        String encrypted = resolveValue(configKey, tenantId, userId);
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        // decrypt() 内部已处理 enc: 前缀检查，非密文原样返回
        return cryptoUtil.decrypt(encrypted);
    }

    /**
     * 保存搜索引擎配置（API Key 加密存储）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveSearchConfig(String scope, Long tenantId, String ownerId,
                                 String configKey, String value) {
        if (!SEARCH_CONFIG_KEYS.contains(configKey)) {
            throw new BizException(ResultCode.PARAM_ERROR, "未知的搜索配置键：" + configKey);
        }
        // API Key 类配置加密存储，URL 类明文存储
        String storedValue = value;
        if (StringUtils.hasText(value) && configKey.endsWith(".api_key")) {
            storedValue = cryptoUtil.encrypt(value);
        }
        saveAgentConfig(scope, tenantId, ownerId, configKey, storedValue,
                SEARCH_CONFIG_LABELS.getOrDefault(configKey, configKey));
    }

    /**
     * 查询某作用域下的搜索引擎配置（API Key 脱敏回显）。
     */
    public List<AgentConfigItem> listSearchConfigs(String scope, Long tenantId, String ownerId) {
        List<AgentConfigItem> all = agentConfigMapper.selectList(
                new LambdaQueryWrapper<AgentConfigItem>()
                        .eq(AgentConfigItem::getScope, scope)
                        .eq(AgentConfigItem::getTenantId, tenantId == null ? 0L : tenantId)
                        .eq(ownerId != null, AgentConfigItem::getOwnerId, ownerId)
                        .isNull(ownerId == null, AgentConfigItem::getOwnerId)
                        .in(AgentConfigItem::getConfigKey, SEARCH_CONFIG_KEYS)
                        .orderByAsc(AgentConfigItem::getConfigKey));
        // API Key 脱敏：enc:xxx → ****
        all.forEach(item -> {
            if (item.getConfigKey().endsWith(".api_key") && StringUtils.hasText(item.getConfigValue())) {
                item.setConfigValue("****");
            }
        });
        return all;
    }

    private final AgentConfigMapper agentConfigMapper;
    private final ModelProviderConfigMapper providerConfigMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CryptoUtil cryptoUtil;
    /** 配置单键值缓存（scope:tenantId:ownerId:configKey → value） */
    private final Cache<String, String> configValueCache;
    /** 当前生效提供商缓存（scope:tenantId:ownerId → ModelProviderConfig） */
    private final Cache<String, ModelProviderConfig> providerConfigCache;

    // ------------------------------------------------------------
    // 读取：三级作用域解析（USER > TENANT > GLOBAL）
    // ------------------------------------------------------------

    /**
     * 解析配置值：一次 OR 查询拉取所有适用作用域，内存按 USER > TENANT > PLATFORM 选取。
     * <p>
     * 优化前逐级查库（最多 3 次 SQL），优化后合并为 1 次 OR 查询。
     * 缓存键仍按最终结果维度（key:tenantId:userId）缓存，写入侧负责失效。
     *
     * @param key      配置键
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @return 配置值；全部未配置时返回 null
     */
    public String resolveValue(String key, Long tenantId, String userId) {
        String cacheKey = "resolved:" + key + ":" + (tenantId == null ? 0L : tenantId) + ":" + userId;
        return configValueCache.get(cacheKey, k -> doResolveValue(key, tenantId, userId));
    }

    /**
     * 三级作用域单次查询：OR 条件合并 USER/TENANT/PLATFORM，按优先级排序取首条。
     */
    private String doResolveValue(String key, Long tenantId, String userId) {
        LambdaQueryWrapper<AgentConfigItem> wrapper = new LambdaQueryWrapper<AgentConfigItem>()
                .eq(AgentConfigItem::getConfigKey, key);
        boolean hasUser = StringUtils.hasText(userId);
        boolean hasTenant = tenantId != null;
        // OR 条件合并三个作用域的查询条件
        wrapper.and(w -> {
            int idx = 0;
            if (hasUser) {
                w.or(idx++ > 0).nested(n -> n
                        .eq(AgentConfigItem::getScope, SCOPE_USER)
                        .eq(AgentConfigItem::getTenantId, tenantId)
                        .eq(AgentConfigItem::getOwnerId, userId));
            }
            if (hasTenant) {
                w.or(idx++ > 0).nested(n -> n
                        .eq(AgentConfigItem::getScope, SCOPE_TENANT)
                        .eq(AgentConfigItem::getTenantId, tenantId)
                        .isNull(AgentConfigItem::getOwnerId));
            }
            w.or(idx > 0).nested(n -> n
                    .eq(AgentConfigItem::getScope, SCOPE_PLATFORM)
                    .eq(AgentConfigItem::getTenantId, 0L)
                    .isNull(AgentConfigItem::getOwnerId));
        });
        List<AgentConfigItem> items = agentConfigMapper.selectList(wrapper);
        if (items.isEmpty()) {
            return null;
        }
        // 按作用域优先级排序：USER(0) > TENANT(1) > PLATFORM(2)，取最高优先级
        return items.stream()
                .min(Comparator.comparingInt(item -> {
                    switch (item.getScope()) {
                        case SCOPE_USER: return 0;
                        case SCOPE_TENANT: return 1;
                        default: return 2;
                    }
                }))
                .map(AgentConfigItem::getConfigValue)
                .orElse(null);
    }

    /** 解析整型配置值，未配置或非法时返回默认值 */
    public int resolveInt(String key, Long tenantId, String userId, int defaultValue) {
        String v = resolveValue(key, tenantId, userId);
        if (!StringUtils.hasText(v)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值非法（{}），使用默认值 {}", key, v, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 解析当前生效的模型提供商配置（含解密后的 API Key）。
     * <p>
     * 在生效作用域内取 is_current=1 且启用的记录；作用域本身按就近覆盖：
     * 用户级有 is_current 记录则用之，否则租户级，最后全局。
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @return 提供商配置（apiKey 已解密）；未配置任何提供商时抛业务异常
     */
    public ModelProviderConfig resolveCurrentProvider(Long tenantId, String userId) {
        ModelProviderConfig cfg = null;
        if (StringUtils.hasText(userId)) {
            cfg = selectCurrentProvider(SCOPE_USER, tenantId, userId);
        }
        if (cfg == null && tenantId != null) {
            cfg = selectCurrentProvider(SCOPE_TENANT, tenantId, null);
        }
        if (cfg == null) {
            cfg = selectCurrentProvider(SCOPE_PLATFORM, 0L, null);
        }
        if (cfg == null) {
            throw new BizException(ResultCode.AGENT_ERROR,
                    "未配置模型提供商，请在系统管理中配置（模型提供商）");
        }
        decryptApiKey(cfg);
        return cfg;
    }

    /** 查询某作用域下全部提供商配置（管理界面用，API Key 脱敏返回） */
    public List<ModelProviderConfig> listProviders(String scope, Long tenantId, String ownerId) {
        List<ModelProviderConfig> list = providerConfigMapper.selectList(
                new LambdaQueryWrapper<ModelProviderConfig>()
                        .eq(ModelProviderConfig::getScope, scope)
                        .eq(ModelProviderConfig::getTenantId, tenantId == null ? 0L : tenantId)
                        .eq(ownerId != null, ModelProviderConfig::getOwnerId, ownerId)
                        .orderByAsc(ModelProviderConfig::getProvider));
        list.forEach(item -> item.setApiKey(cryptoUtil.mask(item.getApiKey())));
        return list;
    }

    /** 查询某作用域下全部运行参数（管理界面用，敏感键脱敏回显） */
    public List<AgentConfigItem> listAgentConfigs(String scope, Long tenantId, String ownerId) {
        List<AgentConfigItem> list = agentConfigMapper.selectList(
                new LambdaQueryWrapper<AgentConfigItem>()
                        .eq(AgentConfigItem::getScope, scope)
                        .eq(AgentConfigItem::getTenantId, tenantId == null ? 0L : tenantId)
                        .eq(ownerId != null, AgentConfigItem::getOwnerId, ownerId)
                        .orderByAsc(AgentConfigItem::getConfigKey));
        // 敏感键（api_key / secret_key）脱敏回显，避免前端明文暴露
        list.forEach(item -> {
            if (isSensitiveKey(item.getConfigKey()) && StringUtils.hasText(item.getConfigValue())) {
                item.setConfigValue(cryptoUtil.mask(item.getConfigValue()));
            }
        });
        return list;
    }

    /**
     * 已知运行参数目录（键/说明/默认值/可选值）。
     * <p>
     * 管理页面「快速添加」的数据源：避免管理员凭空猜键名；
     * 新增可识别的参数键时必须同步登记到本目录（与 KEY_* 常量一一对应）。
     *
     * @return 参数目录（只读，不含当前值）
     */
    public List<ParamKeyInfo> listParamKeys() {
        return List.of(
                // ---- Agent 运行参数 ----
                ParamKeyInfo.builder()
                        .key(KEY_STATE_STORE_TYPE)
                        .description("Agent 会话状态存储：redis（分布式，支持多实例）/ json（本地文件，单机开发）")
                        .defaultValue("redis")
                        .options(List.of("redis", "json"))
                        .build(),
                ParamKeyInfo.builder()
                        .key(KEY_PERMISSION_MODE)
                        .description("权限模式（工具执行审批策略）：DEFAULT 逐项确认 / ACCEPT_EDITS 自动放行编辑 / "
                                + "EXPLORE 只读探索 / BYPASS 完全放行 / DONT_ASK 无人值守")
                        .defaultValue("DEFAULT")
                        .options(List.of("DEFAULT", "ACCEPT_EDITS", "EXPLORE", "BYPASS", "DONT_ASK"))
                        .build(),
                ParamKeyInfo.builder()
                        .key(KEY_COMPACTION_TRIGGER)
                        .description("上下文压缩触发阈值（会话消息条数达到后自动压缩）")
                        .defaultValue("30")
                        .build(),
                ParamKeyInfo.builder()
                        .key(KEY_COMPACTION_KEEP)
                        .description("上下文压缩时保留的最近消息条数")
                        .defaultValue("10")
                        .build(),
                ParamKeyInfo.builder()
                        .key(KEY_MEMORY_FLUSH_MINUTES)
                        .description("长期记忆 flush 节流间隔（分钟，防止频繁落盘）")
                        .defaultValue("10")
                        .build(),
                // ---- 搜索引擎 API Key ----
                ParamKeyInfo.builder()
                        .key(KEY_SEARCH_TAVILY_API_KEY)
                        .description("Tavily API Key（AI Agent 专属搜索，1000 次/月免费）")
                        .build(),
                ParamKeyInfo.builder()
                        .key(KEY_SEARCH_BRAVE_API_KEY)
                        .description("Brave API Key（独立索引，~1000 次/月免费）")
                        .build(),
                ParamKeyInfo.builder()
                        .key(KEY_SEARCH_BING_API_KEY)
                        .description("Bing API Key（Azure 门户创建 Bing Search v7 资源获取）")
                        .build(),
                ParamKeyInfo.builder()
                        .key(KEY_SEARCH_SEARXNG_BASE_URL)
                        .description("SearXNG 实例地址（本地 Docker: http://localhost:8888 或公共实例）")
                        .build(),
                // ---- OCR 识别 - 百度智能云 ----
                ParamKeyInfo.builder()
                        .key(KEY_BAIDU_OCR_API_KEY)
                        .description("OCR API Key - 百度智能云（控制台获取：https://console.bce.baidu.com/ai/#/ai/ocr/overview/index）")
                        .build(),
                ParamKeyInfo.builder()
                        .key(KEY_BAIDU_OCR_SECRET_KEY)
                        .description("OCR Secret Key - 百度智能云（与 API Key 配对使用）")
                        .build(),
                // ---- OCR 识别 - 腾讯云 ----
                ParamKeyInfo.builder()
                        .key(KEY_TENCENT_OCR_SECRET_ID)
                        .description("OCR SecretId - 腾讯云（控制台获取：https://console.cloud.tencent.com/cam/capi）")
                        .build(),
                ParamKeyInfo.builder()
                        .key(KEY_TENCENT_OCR_SECRET_KEY)
                        .description("OCR SecretKey - 腾讯云（与 SecretId 配对使用）")
                        .build()
        );
    }

    // ------------------------------------------------------------
    // 写入：保存或更新（upsert），成功后发布变更事件触发 Agent 热重建
    // ------------------------------------------------------------

    /**
     * 保存/更新运行参数（按 作用域+租户+归属+键 唯一定位）。
     * <p>
     * 敏感键（api_key / secret_key）自动加密存储；
     * 前端回传脱敏值（**** / ab****cd）时保留原密文，不覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAgentConfig(String scope, Long tenantId, String ownerId,
                                String key, String value, String remark) {
        // 敏感键：前端回传脱敏值时保留原密文，不覆盖
        if (isSensitiveKey(key) && CryptoUtil.isMasked(value)) {
            return;
        }
        // 敏感键加密存储（普通参数明文存储）
        String storedValue = value;
        if (isSensitiveKey(key) && StringUtils.hasText(value) && !value.startsWith("enc:")) {
            storedValue = cryptoUtil.encrypt(value);
        }
        AgentConfigItem existed = agentConfigMapper.selectOne(
                new LambdaQueryWrapper<AgentConfigItem>()
                        .eq(AgentConfigItem::getScope, scope)
                        .eq(AgentConfigItem::getTenantId, tenantId == null ? 0L : tenantId)
                        .eq(ownerId != null, AgentConfigItem::getOwnerId, ownerId)
                        .isNull(ownerId == null, AgentConfigItem::getOwnerId)
                        .eq(AgentConfigItem::getConfigKey, key));
        if (existed == null) {
            AgentConfigItem item = new AgentConfigItem();
            item.setScope(scope);
            item.setTenantId(tenantId == null ? 0L : tenantId);
            item.setOwnerId(ownerId);
            item.setConfigKey(key);
            item.setConfigValue(storedValue);
            item.setRemark(remark);
            agentConfigMapper.insert(item);
        } else {
            existed.setConfigValue(storedValue);
            if (StringUtils.hasText(remark)) {
                existed.setRemark(remark);
            }
            agentConfigMapper.updateById(existed);
        }
        publishChanged(scope, tenantId, ownerId);
        // 失效 resolveValue 的合并缓存 + 精确失效单键缓存（兜底）
        String resolvedKey = "resolved:" + key + ":" + (tenantId == null ? 0L : tenantId) + ":" + ownerId;
        configValueCache.invalidate(resolvedKey);
        String valueCacheKey = scope + ":" + (tenantId == null ? 0L : tenantId) + ":" + ownerId + ":" + key;
        configValueCache.invalidate(valueCacheKey);
    }

    /**
     * 保存/更新模型提供商配置；apiKey 为空串时保留原密钥（编辑场景不回显明文）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveProviderConfig(ModelProviderConfig cfg) {
        ModelProviderConfig existed = providerConfigMapper.selectOne(
                new LambdaQueryWrapper<ModelProviderConfig>()
                        .eq(ModelProviderConfig::getScope, cfg.getScope())
                        .eq(ModelProviderConfig::getTenantId, cfg.getTenantId() == null ? 0L : cfg.getTenantId())
                        .eq(cfg.getOwnerId() != null, ModelProviderConfig::getOwnerId, cfg.getOwnerId())
                        .isNull(cfg.getOwnerId() == null, ModelProviderConfig::getOwnerId)
                        .eq(ModelProviderConfig::getProvider, cfg.getProvider()));
        // 同作用域切换当前提供商：先把该作用域内其他记录置为非当前
        if (cfg.isCurrentProvider()) {
            List<ModelProviderConfig> others = providerConfigMapper.selectList(
                    new LambdaQueryWrapper<ModelProviderConfig>()
                            .eq(ModelProviderConfig::getScope, cfg.getScope())
                            .eq(ModelProviderConfig::getTenantId, cfg.getTenantId() == null ? 0L : cfg.getTenantId())
                            .eq(cfg.getOwnerId() != null, ModelProviderConfig::getOwnerId, cfg.getOwnerId())
                            .ne(ModelProviderConfig::getProvider, cfg.getProvider())
                            .eq(ModelProviderConfig::getIsCurrent, 1));
            others.forEach(o -> {
                o.setIsCurrent(0);
                providerConfigMapper.updateById(o);
            });
        }
        if (existed == null) {
            encryptApiKey(cfg);
            providerConfigMapper.insert(cfg);
        } else {
            existed.setDisplayName(cfg.getDisplayName());
            existed.setEnabled(cfg.getEnabled());
            existed.setIsCurrent(cfg.getIsCurrent());
            existed.setBaseUrl(cfg.getBaseUrl());
            existed.setModelName(cfg.getModelName());
            existed.setExtraConfig(cfg.getExtraConfig());
            existed.setRemark(cfg.getRemark());
            // apiKey 为空或掩码值时不覆盖原密钥
            if (StringUtils.hasText(cfg.getApiKey()) && !CryptoUtil.isMasked(cfg.getApiKey())) {
                existed.setApiKey(cryptoUtil.encrypt(cfg.getApiKey()));
            }
            providerConfigMapper.updateById(existed);
        }
        publishChanged(cfg.getScope(), cfg.getTenantId(), cfg.getOwnerId());
        // 失效提供商缓存：按作用域精确失效
        String providerCacheKey = cfg.getScope() + ":" + (cfg.getTenantId() == null ? 0L : cfg.getTenantId()) + ":" + cfg.getOwnerId();
        providerConfigCache.invalidate(providerCacheKey);
    }

    // ------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------

    /**
     * 查询指定作用域内当前生效且启用的提供商（带本地缓存）。
     * <p>
     * 缓存键：scope:tenantId:ownerId；返回的是缓存副本，调用方可安全解密。
     * 写入侧 {@link #saveProviderConfig} 负责失效。
     */
    private ModelProviderConfig selectCurrentProvider(String scope, Long tenantId, String ownerId) {
        String cacheKey = scope + ":" + (tenantId == null ? 0L : tenantId) + ":" + ownerId;
        ModelProviderConfig cached = providerConfigCache.getIfPresent(cacheKey);
        if (cached != null) {
            return copyProvider(cached);
        }
        ModelProviderConfig result = providerConfigMapper.selectOne(
                new LambdaQueryWrapper<ModelProviderConfig>()
                        .eq(ModelProviderConfig::getScope, scope)
                        .eq(ModelProviderConfig::getTenantId, tenantId == null ? 0L : tenantId)
                        .eq(ownerId != null, ModelProviderConfig::getOwnerId, ownerId)
                        .isNull(ownerId == null, ModelProviderConfig::getOwnerId)
                        .eq(ModelProviderConfig::getIsCurrent, 1)
                        .eq(ModelProviderConfig::getEnabled, 1)
                        .last("LIMIT 1"));
        if (result != null) {
            providerConfigCache.put(cacheKey, result);
        }
        return result;
    }

    /**
     * 浅拷贝提供商配置（避免解密操作污染缓存中的原始对象）。
     */
    private ModelProviderConfig copyProvider(ModelProviderConfig src) {
        ModelProviderConfig copy = new ModelProviderConfig();
        copy.setId(src.getId());
        copy.setScope(src.getScope());
        copy.setTenantId(src.getTenantId());
        copy.setOwnerId(src.getOwnerId());
        copy.setProvider(src.getProvider());
        copy.setDisplayName(src.getDisplayName());
        copy.setEnabled(src.getEnabled());
        copy.setIsCurrent(src.getIsCurrent());
        copy.setApiKey(src.getApiKey());
        copy.setBaseUrl(src.getBaseUrl());
        copy.setModelName(src.getModelName());
        copy.setExtraConfig(src.getExtraConfig());
        copy.setRemark(src.getRemark());
        copy.setCreateTime(src.getCreateTime());
        copy.setUpdateTime(src.getUpdateTime());
        copy.setCreator(src.getCreator());
        copy.setUpdater(src.getUpdater());
        copy.setCreatorId(src.getCreatorId());
        copy.setUpdaterId(src.getUpdaterId());
        return copy;
    }

    /** 解密 API Key（enc: 前缀；同时兜底读取环境变量，便于容器化部署注入密钥） */
    private void decryptApiKey(ModelProviderConfig cfg) {
        String raw = cfg.getApiKey();
        if (StringUtils.hasText(raw)) {
            cfg.setApiKey(cryptoUtil.decrypt(raw));
            return;
        }
        // 库中未配置时兜底环境变量（不写回库）
        String provider = cfg.getProvider() == null ? "" : cfg.getProvider().toLowerCase(Locale.ROOT);
        String envKey = switch (provider) {
            case "dashscope" -> System.getenv("DASHSCOPE_API_KEY");
            case "deepseek" -> System.getenv("DEEPSEEK_API_KEY");
            case "openai" -> System.getenv("OPENAI_API_KEY");
            default -> null;
        };
        if (StringUtils.hasText(envKey)) {
            cfg.setApiKey(envKey);
        }
    }

    /** 插入前加密 API Key */
    private void encryptApiKey(ModelProviderConfig cfg) {
        if (StringUtils.hasText(cfg.getApiKey())) {
            cfg.setApiKey(cryptoUtil.encrypt(cfg.getApiKey()));
        }
    }

    /** 发布配置变更事件，触发受影响用户的 Agent 热重建（携带归属人供 USER 级精准失效） */
    private void publishChanged(String scope, Long tenantId, String ownerId) {
        eventPublisher.publishEvent(new ConfigChangedEvent(this, scope, tenantId, ownerId));
        log.debug("配置已更新并发布变更事件: scope={}, tenantId={}, owner={}", scope, tenantId, ownerId);
    }

    /**
     * 判断配置键是否为敏感键（API Key / Secret Key），需要加密存储与脱敏回显。
     */
    private boolean isSensitiveKey(String key) {
        return key != null && (key.endsWith(".api_key")
                || key.endsWith(".secret_key")
                || key.endsWith(".secret_id"));
    }
}
