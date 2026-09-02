package com.claw.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.claw.agent.common.BizException;
import com.claw.agent.common.CryptoUtil;
import com.claw.agent.common.ResultCode;
import com.claw.agent.common.ToolCodes;
import com.claw.agent.config.infra.ClawProperties;
import com.claw.agent.mapper.McpServerMapper;
import com.claw.agent.mapper.SkillConfigMapper;
import com.claw.agent.mapper.TenantMapper;
import com.claw.agent.mapper.ToolConfigMapper;
import com.claw.agent.model.McpServer;
import com.claw.agent.model.SkillConfig;
import com.claw.agent.model.Tenant;
import com.claw.agent.model.ToolConfig;
import com.claw.agent.model.dto.SkillInfo;
import com.claw.agent.model.dto.ToolKeyInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.harness.agent.tools.McpServerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Agent 能力配置服务：MCP 服务器 / 工具开关 / 技能开关（三级作用域）。
 * <p>
 * 与 {@link ConfigService} 同范式：按 作用域+租户+归属 唯一定位，
 * 生效解析就近覆盖（USER &gt; TENANT &gt; GLOBAL），无记录视为默认启用；
 * 写入成功后发布 {@link ConfigChangedEvent}，AgentRegistry 按作用域热重建受影响用户的 Agent。
 * <p>
 * MCP 的 headers / env 可含密钥，一律 AES 加密存储，列表接口掩码回显。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapabilityService {

    /** 工具键：文件系统读写（内置） */
    public static final String TOOL_FILESYSTEM = ToolCodes.FILESYSTEM;
    /** 工具键：Shell 命令执行（内置） */
    public static final String TOOL_SHELL = ToolCodes.SHELL;
    /** 工具键：长期记忆（内置） */
    public static final String TOOL_MEMORY = ToolCodes.MEMORY;
    /** 工具键：知识库笔记（自定义） */
    public static final String TOOL_NOTE = ToolCodes.NOTE_TOOLS;
    /** 工具键：联网搜索（多引擎降级） */
    public static final String TOOL_WEB_SEARCH = ToolCodes.MULTI_SEARCH;
    /** 工具键：邮件发送（自定义） */
    public static final String TOOL_EMAIL = ToolCodes.EMAIL_TOOLS;

    private final McpServerMapper mcpServerMapper;
    private final ToolConfigMapper toolConfigMapper;
    private final SkillConfigMapper skillConfigMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CryptoUtil cryptoUtil;
    private final ClawProperties properties;
    private final ObjectMapper objectMapper;
    private final TenantMapper tenantMapper;

    /** tenantId → tenantCode 缓存（避免重复查库） */
    private final Map<Long, String> tenantCodeCache = new java.util.concurrent.ConcurrentHashMap<>();

    // ------------------------------------------------------------
    // MCP 服务器
    // ------------------------------------------------------------

    /** 查询某作用域下 MCP 服务器列表（敏感字段掩码回显） */
    public List<McpServer> listMcpServers(String scope, Long tenantId, String ownerId) {
        List<McpServer> list = mcpServerMapper.selectList(serverWrapper(scope, tenantId, ownerId)
                .orderByAsc(McpServer::getName));
        list.forEach(s -> {
            s.setHeaders(cryptoUtil.mask(s.getHeaders()));
            s.setEnv(cryptoUtil.mask(s.getEnv()));
        });
        return list;
    }

    /**
     * 保存/更新 MCP 服务器（按 作用域+租户+归属+名称 唯一定位）。
     * headers / env 为空或掩码值时保留原密文（编辑不回显明文）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMcpServer(McpServer cfg) {
        if (!StringUtils.hasText(cfg.getName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "MCP 服务器名称不能为空");
        }
        validateTransport(cfg);
        McpServer existed = mcpServerMapper.selectOne(serverWrapper(cfg.getScope(), cfg.getTenantId(), cfg.getOwnerId())
                .eq(McpServer::getName, cfg.getName())
                .last("LIMIT 1"));
        if (existed == null) {
            cfg.setHeaders(cryptoUtil.encrypt(cfg.getHeaders()));
            cfg.setEnv(cryptoUtil.encrypt(cfg.getEnv()));
            mcpServerMapper.insert(cfg);
        } else {
            existed.setTransport(cfg.getTransport());
            existed.setCommand(cfg.getCommand());
            existed.setArgs(cfg.getArgs());
            existed.setUrl(cfg.getUrl());
            existed.setEnableTools(cfg.getEnableTools());
            existed.setEnabled(cfg.getEnabled());
            existed.setRemark(cfg.getRemark());
            keepSecret(existed::setHeaders, existed.getHeaders(), cfg.getHeaders());
            keepSecret(existed::setEnv, existed.getEnv(), cfg.getEnv());
            mcpServerMapper.updateById(existed);
        }
        publishChanged(cfg.getScope(), cfg.getTenantId(), cfg.getOwnerId());
    }

    /** 删除 MCP 服务器（归属校验：仅能删除本作用域内自己可见的记录） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMcpServer(Long id, String scope, Long tenantId, String ownerId) {
        McpServer existed = mcpServerMapper.selectOne(serverWrapper(scope, tenantId, ownerId)
                .eq(McpServer::getId, id)
                .last("LIMIT 1"));
        if (existed == null) {
            throw new BizException(ResultCode.NOT_FOUND, "MCP 服务器不存在或无权删除");
        }
        mcpServerMapper.deleteById(existed.getId());
        publishChanged(scope, tenantId, ownerId);
    }

    /**
     * 解析用户当前生效的 MCP 服务器（构建 Agent 用）。
     * <p>
     * 按名称就近覆盖：GLOBAL 打底 → TENANT 覆盖 → USER 覆盖；仅取启用项，
     * 返回 Harness 的 {@link McpServerConfig} 映射（敏感字段已解密）。
     */
    public Map<String, McpServerConfig> resolveMcpServers(Long tenantId, String userId) {
        // 单次 OR 查询拉取所有作用域的 MCP 服务器，避免逐级查库（优化前 3 次 SQL → 1 次）
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<>();
        boolean hasUser = StringUtils.hasText(userId);
        boolean hasTenant = tenantId != null;
        wrapper.and(w -> {
            int idx = 0;
            if (hasUser) {
                w.or(idx++ > 0).nested(n -> n
                        .eq(McpServer::getScope, ConfigService.SCOPE_USER)
                        .eq(McpServer::getTenantId, tenantId)
                        .eq(McpServer::getOwnerId, userId));
            }
            if (hasTenant) {
                w.or(idx++ > 0).nested(n -> n
                        .eq(McpServer::getScope, ConfigService.SCOPE_TENANT)
                        .eq(McpServer::getTenantId, tenantId)
                        .isNull(McpServer::getOwnerId));
            }
            w.or(idx > 0).nested(n -> n
                    .eq(McpServer::getScope, ConfigService.SCOPE_PLATFORM)
                    .eq(McpServer::getTenantId, 0L)
                    .isNull(McpServer::getOwnerId));
        });
        List<McpServer> allServers = mcpServerMapper.selectList(wrapper);

        // 按作用域优先级排序后依次覆盖：PLATFORM(低) → TENANT → USER(高)
        Map<String, McpServer> merged = new LinkedHashMap<>();
        allServers.stream()
                .sorted(Comparator.comparingInt(s -> {
                    switch (s.getScope()) {
                        case ConfigService.SCOPE_USER: return 2;
                        case ConfigService.SCOPE_TENANT: return 1;
                        default: return 0;
                    }
                }))
                .forEach(s -> merged.put(s.getName(), s));

        Map<String, McpServerConfig> result = new LinkedHashMap<>();
        merged.forEach((name, server) -> {
            if (isOn(server.getEnabled())) {
                result.put(name, toHarnessConfig(server));
            }
        });
        return result;
    }

    // ------------------------------------------------------------
    // 工具开关
    // ------------------------------------------------------------

    /**
     * 可开关工具目录（键/名称/说明/类型）。
     * 管理页工具开关卡片的数据源；新增可开关工具时必须同步登记。
     */
    public List<ToolKeyInfo> listToolKeys() {
        return List.of(
                ToolKeyInfo.builder().key(TOOL_FILESYSTEM).name("文件读写")
                        .description("工作区内文件的读写/搜索/目录操作").type("builtin").build(),
                ToolKeyInfo.builder().key(TOOL_SHELL).name("Shell 命令")
                        .description("执行系统命令完成自动化任务，危险操作先征求确认").type("builtin").build(),
                ToolKeyInfo.builder().key(TOOL_MEMORY).name("长期记忆")
                        .description("跨会话沉淀用户偏好与事实").type("builtin").build(),
                ToolKeyInfo.builder().key(TOOL_NOTE).name("知识库笔记")
                        .description("用户工作区笔记的增删改查").type("custom").build(),
                ToolKeyInfo.builder().key(TOOL_WEB_SEARCH).name("联网搜索")
                        .description("多引擎降级搜索（Tavily/Brave/Bing/SearXNG/DuckDuckGo）").type("custom").build(),
                ToolKeyInfo.builder().key(TOOL_EMAIL).name("邮件发送")
                        .description("通过 SMTP 发送邮件通知，需先配置邮箱账号").type("custom").build(),
                ToolKeyInfo.builder().key(ToolCodes.OCR).name("OCR识别")
                        .description("OCR 图片文字识别（多厂商降级：百度智能云 → 腾讯云），支持印刷体和手写体").type("custom").build(),
                ToolKeyInfo.builder().key(ToolCodes.DOCUMENT_PARSE).name("文档解析")
                        .description("基于 Apache Tika 从 PDF/DOCX/XLSX/PPTX 等文件中提取文本内容").type("custom").build());
    }

    /** 查询某作用域下显式登记的工具开关（未登记的视为默认启用，不在结果中） */
    public List<ToolConfig> listToolConfigs(String scope, Long tenantId, String ownerId) {
        return toolConfigMapper.selectList(toolWrapper(scope, tenantId, ownerId)
                .orderByAsc(ToolConfig::getToolKey));
    }

    /** 保存工具开关（按 作用域+租户+归属+工具键 唯一定位） */
    @Transactional(rollbackFor = Exception.class)
    public void saveToolConfig(String scope, Long tenantId, String ownerId,
                               String toolKey, int enabled) {
        boolean known = listToolKeys().stream().anyMatch(k -> k.getKey().equals(toolKey));
        if (!known) {
            throw new BizException(ResultCode.PARAM_ERROR, "未知工具键：" + toolKey);
        }
        ToolConfig existed = toolConfigMapper.selectOne(toolWrapper(scope, tenantId, ownerId)
                .eq(ToolConfig::getToolKey, toolKey)
                .last("LIMIT 1"));
        if (existed == null) {
            ToolConfig item = new ToolConfig();
            item.setScope(scope);
            item.setTenantId(tenantId == null ? 0L : tenantId);
            item.setOwnerId(ownerId);
            item.setToolKey(toolKey);
            item.setEnabled(enabled);
            toolConfigMapper.insert(item);
        } else {
            existed.setEnabled(enabled);
            toolConfigMapper.updateById(existed);
        }
        publishChanged(scope, tenantId, ownerId);
    }

    /** 解析工具是否启用：单次 OR 查询拉取三级作用域，按 USER > TENANT > PLATFORM 就近取值，未配置默认启用 */
    public boolean isToolEnabled(String toolKey, Long tenantId, String userId) {
        LambdaQueryWrapper<ToolConfig> wrapper = new LambdaQueryWrapper<ToolConfig>()
                .eq(ToolConfig::getToolKey, toolKey);
        boolean hasUser = StringUtils.hasText(userId);
        boolean hasTenant = tenantId != null;
        wrapper.and(w -> {
            int idx = 0;
            if (hasUser) {
                w.or(idx++ > 0).nested(n -> n
                        .eq(ToolConfig::getScope, ConfigService.SCOPE_USER)
                        .eq(ToolConfig::getTenantId, tenantId)
                        .eq(ToolConfig::getOwnerId, userId));
            }
            if (hasTenant) {
                w.or(idx++ > 0).nested(n -> n
                        .eq(ToolConfig::getScope, ConfigService.SCOPE_TENANT)
                        .eq(ToolConfig::getTenantId, tenantId)
                        .isNull(ToolConfig::getOwnerId));
            }
            w.or(idx > 0).nested(n -> n
                    .eq(ToolConfig::getScope, ConfigService.SCOPE_PLATFORM)
                    .eq(ToolConfig::getTenantId, 0L)
                    .isNull(ToolConfig::getOwnerId));
        });
        List<ToolConfig> configs = toolConfigMapper.selectList(wrapper);
        if (configs.isEmpty()) {
            return true;
        }
        // 按优先级选取：USER(0) > TENANT(1) > PLATFORM(2)
        return configs.stream()
                .min(Comparator.comparingInt(c -> {
                    switch (c.getScope()) {
                        case ConfigService.SCOPE_USER: return 0;
                        case ConfigService.SCOPE_TENANT: return 1;
                        default: return 2;
                    }
                }))
                .map(c -> isOn(c.getEnabled()))
                .orElse(true);
    }

    // ------------------------------------------------------------
    // 技能开关
    // ------------------------------------------------------------

    /** 保存技能开关（按 作用域+租户+归属+技能名 唯一定位） */
    @Transactional(rollbackFor = Exception.class)
    public void saveSkillConfig(String scope, Long tenantId, String ownerId,
                                String skillName, int enabled) {
        if (!StringUtils.hasText(skillName)) {
            throw new BizException(ResultCode.PARAM_ERROR, "技能名不能为空");
        }
        SkillConfig existed = skillConfigMapper.selectOne(skillWrapper(scope, tenantId, ownerId)
                .eq(SkillConfig::getSkillName, skillName)
                .last("LIMIT 1"));
        if (existed == null) {
            SkillConfig item = new SkillConfig();
            item.setScope(scope);
            item.setTenantId(tenantId == null ? 0L : tenantId);
            item.setOwnerId(ownerId);
            item.setSkillName(skillName);
            item.setEnabled(enabled);
            skillConfigMapper.insert(item);
        } else {
            existed.setEnabled(enabled);
            skillConfigMapper.updateById(existed);
        }
        publishChanged(scope, tenantId, ownerId);
    }

    /**
     * 扫描用户工作区技能并附带生效启停状态（管理页技能卡片用）。
     * <p>
     * 技能目录约定：工作区/{租户}/{用户}/skills/&lt;技能名&gt;/SKILL.md；
     * 描述解析自 SKILL.md frontmatter 的 description 字段，解析失败仅留空不报错。
     */
    public List<SkillInfo> listUserSkills(Long tenantId, String userId) {
        Path skillsDir = userWorkspace(tenantId, userId).resolve("skills");
        List<SkillInfo> result = new ArrayList<>();
        if (!Files.isDirectory(skillsDir)) {
            return result;
        }
        try (Stream<Path> stream = Files.list(skillsDir)) {
            stream.filter(Files::isDirectory).sorted().forEach(dir -> {
                String name = dir.getFileName().toString();
                Path skillMd = dir.resolve("SKILL.md");
                result.add(SkillInfo.builder()
                        .name(name)
                        .description(Files.exists(skillMd) ? parseSkillDescription(skillMd) : null)
                        .enabled(isSkillEnabled(name, tenantId, userId))
                        .build());
            });
        } catch (IOException e) {
            log.warn("扫描技能目录失败: {}", skillsDir, e);
        }
        return result;
    }

    /** 解析用户当前被禁用的技能名列表（构建 Agent 时交给 disableSkills）。
     *  批量查询所有技能的三级作用域配置，避免逐个查库（优化前 N×3 次 SQL → 1 次） */
    public List<String> resolveDisabledSkills(Long tenantId, String userId) {
        List<SkillInfo> skills = listUserSkills(tenantId, userId);
        if (skills.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> skillNames = skills.stream().map(SkillInfo::getName).toList();

        // 单次 OR 查询拉取所有相关技能的三级作用域配置
        LambdaQueryWrapper<SkillConfig> wrapper = new LambdaQueryWrapper<SkillConfig>()
                .in(SkillConfig::getSkillName, skillNames);
        boolean hasUser = StringUtils.hasText(userId);
        boolean hasTenant = tenantId != null;
        wrapper.and(w -> {
            int idx = 0;
            if (hasUser) {
                w.or(idx++ > 0).nested(n -> n
                        .eq(SkillConfig::getScope, ConfigService.SCOPE_USER)
                        .eq(SkillConfig::getTenantId, tenantId)
                        .eq(SkillConfig::getOwnerId, userId));
            }
            if (hasTenant) {
                w.or(idx++ > 0).nested(n -> n
                        .eq(SkillConfig::getScope, ConfigService.SCOPE_TENANT)
                        .eq(SkillConfig::getTenantId, tenantId)
                        .isNull(SkillConfig::getOwnerId));
            }
            w.or(idx > 0).nested(n -> n
                    .eq(SkillConfig::getScope, ConfigService.SCOPE_PLATFORM)
                    .eq(SkillConfig::getTenantId, 0L)
                    .isNull(SkillConfig::getOwnerId));
        });
        List<SkillConfig> allConfigs = skillConfigMapper.selectList(wrapper);

        // 按技能名分组，每组按作用域优先级合并（USER > TENANT > PLATFORM）
        Map<String, SkillConfig> merged = new LinkedHashMap<>();
        allConfigs.stream()
                .sorted(Comparator.comparingInt(c -> {
                    switch (c.getScope()) {
                        case ConfigService.SCOPE_USER: return 0;
                        case ConfigService.SCOPE_TENANT: return 1;
                        default: return 2;
                    }
                }))
                .forEach(c -> merged.putIfAbsent(c.getSkillName(), c));

        // 收集被禁用的技能
        List<String> disabled = new ArrayList<>();
        for (SkillInfo skill : skills) {
            SkillConfig config = merged.get(skill.getName());
            if (config != null && !isOn(config.getEnabled())) {
                disabled.add(skill.getName());
            }
        }
        return disabled;
    }

    // ------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------

    /**
     * 启用标志判定（1 启用）。
     * <p>
     * 实体上禁止手写 isEnabled()：与 Lombok getEnabled() 并存会让 MyBatis
     * 反射解析 enabled 属性歧，导致 insert 参数绑定报错。
     */
    private static boolean isOn(Integer flag) {
        return Integer.valueOf(1).equals(flag);
    }

    /** 用户工作区路径（与 AgentRegistry 构建规则保持一致：根/{租户编码}/{用户}） */
    private Path userWorkspace(Long tenantId, String userId) {
        String tenantCode = resolveTenantCode(tenantId);
        return Paths.get(properties.getAgent().getWorkspace(), tenantCode, userId);
    }

    /**
     * 解析租户编码：优先从缓存取，未命中则查库并缓存。
     *
     * @param tenantId 租户ID
     * @return 租户编码（不会返回 null）
     */
    private String resolveTenantCode(Long tenantId) {
        if (tenantId == null) {
            return "0";
        }
        return tenantCodeCache.computeIfAbsent(tenantId, id -> {
            Tenant tenant = tenantMapper.selectById(id);
            if (tenant != null && StringUtils.hasText(tenant.getTenantCode())) {
                return tenant.getTenantCode();
            }
            log.warn("租户 {} 不存在或无编码，工作区目录回退为数字ID", id);
            return String.valueOf(id);
        });
    }

    /** 解析技能是否启用：单次 OR 查询拉取三级作用域，就近覆盖，未配置默认启用 */
    private boolean isSkillEnabled(String skillName, Long tenantId, String userId) {
        LambdaQueryWrapper<SkillConfig> wrapper = new LambdaQueryWrapper<SkillConfig>()
                .eq(SkillConfig::getSkillName, skillName);
        boolean hasUser = StringUtils.hasText(userId);
        boolean hasTenant = tenantId != null;
        wrapper.and(w -> {
            int idx = 0;
            if (hasUser) {
                w.or(idx++ > 0).nested(n -> n
                        .eq(SkillConfig::getScope, ConfigService.SCOPE_USER)
                        .eq(SkillConfig::getTenantId, tenantId)
                        .eq(SkillConfig::getOwnerId, userId));
            }
            if (hasTenant) {
                w.or(idx++ > 0).nested(n -> n
                        .eq(SkillConfig::getScope, ConfigService.SCOPE_TENANT)
                        .eq(SkillConfig::getTenantId, tenantId)
                        .isNull(SkillConfig::getOwnerId));
            }
            w.or(idx > 0).nested(n -> n
                    .eq(SkillConfig::getScope, ConfigService.SCOPE_PLATFORM)
                    .eq(SkillConfig::getTenantId, 0L)
                    .isNull(SkillConfig::getOwnerId));
        });
        List<SkillConfig> configs = skillConfigMapper.selectList(wrapper);
        if (configs.isEmpty()) {
            return true;
        }
        return configs.stream()
                .min(Comparator.comparingInt(c -> {
                    switch (c.getScope()) {
                        case ConfigService.SCOPE_USER: return 0;
                        case ConfigService.SCOPE_TENANT: return 1;
                        default: return 2;
                    }
                }))
                .map(c -> isOn(c.getEnabled()))
                .orElse(true);
    }

    /** 作用域三元组查询条件（MCP 服务器）：ownerId 为 null 时匹配 NULL */
    private LambdaQueryWrapper<McpServer> serverWrapper(String scope, Long tenantId, String ownerId) {
        return new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getScope, scope)
                .eq(McpServer::getTenantId, tenantId == null ? 0L : tenantId)
                .eq(ownerId != null, McpServer::getOwnerId, ownerId)
                .isNull(ownerId == null, McpServer::getOwnerId);
    }

    private LambdaQueryWrapper<ToolConfig> toolWrapper(String scope, Long tenantId, String ownerId) {
        return new LambdaQueryWrapper<ToolConfig>()
                .eq(ToolConfig::getScope, scope)
                .eq(ToolConfig::getTenantId, tenantId == null ? 0L : tenantId)
                .eq(ownerId != null, ToolConfig::getOwnerId, ownerId)
                .isNull(ownerId == null, ToolConfig::getOwnerId);
    }

    private LambdaQueryWrapper<SkillConfig> skillWrapper(String scope, Long tenantId, String ownerId) {
        return new LambdaQueryWrapper<SkillConfig>()
                .eq(SkillConfig::getScope, scope)
                .eq(SkillConfig::getTenantId, tenantId == null ? 0L : tenantId)
                .eq(ownerId != null, SkillConfig::getOwnerId, ownerId)
                .isNull(ownerId == null, SkillConfig::getOwnerId);
    }

    private List<McpServer> selectServers(String scope, Long tenantId, String ownerId) {
        return mcpServerMapper.selectList(serverWrapper(scope, tenantId, ownerId));
    }

    /** 实体转换为 Harness MCP 配置（敏感字段解密、JSON 字段解析，解析失败仅告警） */
    private McpServerConfig toHarnessConfig(McpServer server) {
        McpServerConfig config = new McpServerConfig();
        config.setTransport(server.getTransport());
        config.setCommand(server.getCommand());
        config.setUrl(server.getUrl());
        config.setArgs(parseStringList(server.getArgs(), server.getName(), "args"));
        config.setEnableTools(parseStringList(server.getEnableTools(), server.getName(), "enableTools"));
        config.setHeaders(parseStringMap(cryptoUtil.decrypt(server.getHeaders()), server.getName(), "headers"));
        config.setEnv(parseStringMap(cryptoUtil.decrypt(server.getEnv()), server.getName(), "env"));
        return config;
    }

    /** 校验传输方式与必填项：stdio 需命令；其余需端点 */
    private void validateTransport(McpServer cfg) {
        String transport = cfg.getTransport();
        if (!StringUtils.hasText(transport)) {
            throw new BizException(ResultCode.PARAM_ERROR, "传输方式不能为空");
        }
        boolean stdio = "stdio".equalsIgnoreCase(transport);
        if (stdio && !StringUtils.hasText(cfg.getCommand())) {
            throw new BizException(ResultCode.PARAM_ERROR, "stdio 传输方式必须填写启动命令");
        }
        if (!stdio && !StringUtils.hasText(cfg.getUrl())) {
            throw new BizException(ResultCode.PARAM_ERROR, "sse / http / streamable-http 传输方式必须填写服务端点");
        }
    }

    /** 敏感字段保留原值策略：回传空或掩码时不覆盖原密文 */
    private void keepSecret(java.util.function.Consumer<String> setter, String oldCipher, String incoming) {
        if (StringUtils.hasText(incoming) && !CryptoUtil.isMasked(incoming)) {
            setter.accept(cryptoUtil.encrypt(incoming));
        } else {
            setter.accept(oldCipher);
        }
    }

    /** 解析 JSON 字符串数组，失败仅告警返回空列表（单个 MCP 配置格式错误不阻断 Agent 构建） */
    private List<String> parseStringList(String json, String serverName, String field) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("MCP 服务器 {} 的 {} 不是合法 JSON 数组，已忽略: {}", serverName, field, e.getMessage());
            return null;
        }
    }

    /** 解析 JSON 字符串映射，失败仅告警返回 null */
    private Map<String, String> parseStringMap(String json, String serverName, String field) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            log.warn("MCP 服务器 {} 的 {} 不是合法 JSON 对象，已忽略: {}", serverName, field, e.getMessage());
            return null;
        }
    }

    /** 从 SKILL.md frontmatter 提取 description（非 YAML 库轻量解析，失败返回 null） */
    private String parseSkillDescription(Path skillMd) {
        try {
            List<String> lines = Files.readAllLines(skillMd);
            boolean inFrontmatter = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.equals("---")) {
                    if (inFrontmatter) {
                        break;
                    }
                    inFrontmatter = true;
                    continue;
                }
                if (inFrontmatter && trimmed.startsWith("description:")) {
                    return trimmed.substring("description:".length()).trim();
                }
            }
        } catch (IOException e) {
            log.warn("解析技能描述失败: {}", skillMd, e);
        }
        return null;
    }

    /** 发布配置变更事件，触发受影响用户的 Agent 热重建 */
    private void publishChanged(String scope, Long tenantId, String ownerId) {
        eventPublisher.publishEvent(new ConfigChangedEvent(this, scope, tenantId, ownerId));
        log.debug("能力配置已更新并发布变更事件: scope={}, tenantId={}, owner={}", scope, tenantId, ownerId);
    }
}
