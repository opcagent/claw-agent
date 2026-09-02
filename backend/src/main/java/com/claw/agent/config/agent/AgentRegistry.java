package com.claw.agent.config.agent;

import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.common.ToolCodes;
import com.claw.agent.config.infra.ClawProperties;
import com.claw.agent.config.tool.ToolRegistry;
import com.claw.agent.mapper.TenantMapper;
import com.claw.agent.model.ModelProviderConfig;
import com.claw.agent.model.Tenant;
import com.claw.agent.service.CapabilityService;
import com.claw.agent.service.ConfigChangedEvent;
import com.claw.agent.service.ConfigService;
import com.claw.agent.service.EmailService;
import com.claw.agent.service.SessionSummaryService;
import com.claw.agent.tool.*;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.middleware.AgentTraceMiddleware;
import com.claw.agent.config.agent.middleware.GuardrailsMiddleware;
import com.claw.agent.config.agent.middleware.PerformanceMiddleware;
import com.claw.agent.config.agent.middleware.ToolCheckMiddleware;
import io.agentscope.harness.agent.skill.curator.SkillCuratorConfig;
import io.agentscope.harness.agent.tool.SkillManageConfig;
import io.agentscope.harness.agent.tools.McpServerConfig;
import io.agentscope.harness.agent.tools.McpServerRegistrar;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 注册表：按"每用户一个满血版 HarnessAgent"管理实例生命周期。
 * <p>
 * 配置完全数据库驱动（三级作用域：USER &gt; TENANT &gt; GLOBAL）：
 * 模型提供商、状态存储类型、权限模式、压缩/记忆参数均由 {@link ConfigService} 解析；
 * 配置变更后监听 {@link ConfigChangedEvent} 失效缓存，下次会话自动热重建，无需重启。
 * <p>
 * 用户隔离：工作区按 租户/用户 分目录（人格/技能/记忆/笔记互不可见）。
 * <p>
 * 已接入的完整能力：
 * <ul>
 *   <li>工作区人格（workspace/AGENTS.md）与会话日志持久化</li>
 *   <li>双层长期记忆（memory flush + consolidation）</li>
 *   <li>上下文自动压缩（compaction）+ 大工具结果卸载（toolResultEviction）</li>
 *   <li>技能自学习闭环（propose_skill / skill_manage + 后台 curator 整理）</li>
 *   <li>计划模式（Plan Mode，只读规划 + HITL 退出）</li>
 *   <li>权限系统 HITL（敏感工具执行前弹确认，前端展示审批按钮）</li>
 *   <li>Agent 执行链路追踪（AgentTraceMiddleware）</li>
 *   <li>状态存储：Redis（分布式，推荐）/ JSON 文件（单机开发）</li>
 *   <li>streamEvents 流式事件（SSE 数据源）+ 同 (userId, sessionId) 自动串行化</li>
 * </ul>
 */
@Slf4j
@Service
public class AgentRegistry {

    /** 状态存储类型：Redis */
    private static final String STORE_TYPE_REDIS = "redis";

    /** Redis key 前缀：多项目共享同一 Redis 时避免冲突 */
    private static final String REDIS_KEY_PREFIX = "claw-agent:session:";

    /** 权限规则来源标识 */
    private static final String RULE_SOURCE_POLICY = "clawPolicy";

    /**
     * 工具执行容错配置：最大重试 3 次，指数退避（1s → 2s → 4s），超时 30s。
     * 覆盖工具执行中的瞬时故障（网络超时、临时性 IO 异常等）。
     * <p>
     * 与模型调用重试（{@link ModelFactory} 中的 MODEL_EXECUTION_CONFIG）分离，
     * 工具执行通常更快完成，因此超时和退避上限更低。
     */
    private static final ExecutionConfig TOOL_EXECUTION_CONFIG = ExecutionConfig.builder()
            .maxAttempts(3)
            .timeout(Duration.ofSeconds(30))
            .initialBackoff(Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(4))
            .backoffMultiplier(2.0)
            .build();

    /** 默认权限模式（数据库未配置时） */
    private static final String DEFAULT_PERMISSION_MODE = "DEFAULT";

    /** Agent 基础系统提示词（人格可由预设模板与工作区 AGENTS.md 进一步叠加） */
    private static final String BASE_SYS_PROMPT = """
            你是 Claw，一名个人全能助理，部署在用户自己的服务器上。你的能力包括：
            1. 文件与工作区管理：读写工作区文件、维护笔记与知识库；
            2. Shell 命令执行：可运行命令完成自动化任务，危险操作会先征求用户确认；
            3. 网络搜索与信息整理；
            4. 图片与文件理解：用户会上传图片或文件，请结合内容作答；
            5. 长期记忆：把用户的重要偏好与事实沉淀到记忆，跨会话延续。
            请始终使用简体中文回复，条理清晰；执行复杂任务前先给出计划。

            ## 安全红线（强制，不可绕过）
            - 禁止通过 write_file / shell 等工具修改平台源码目录：backend/src/、frontend/src/、pom.xml、package.json、.git/、.env、application.yml。
            - 禁止在对话中提议直接写入源码（如「我来帮你实现」「我直接写出完整代码」）。正确做法：给出代码片段供参考，并说明「请在 IDE 中手动修改」。
            - 如果用户要求修改上述文件，必须拒绝并说明：「平台源码受安全策略保护，无法通过对话直接修改，请在 IDE 中手动更改。」
            - 允许在工作区目录（workspace/、notes/、knowledge/、skills/）内自由读写。
            """;

    /** 缓存：用户ID(+预设编码) -> Agent 实例 */
    private final Map<String, HarnessAgent> agents = new ConcurrentHashMap<>();

    /** 缓存：用户ID -> 租户ID（配置变更时按租户精准失效） */
    private final Map<String, Long> userTenants = new ConcurrentHashMap<>();

    /**
     * 共享 Redis 客户端：所有 Agent 的状态存储复用同一实例（Lettuce 线程安全、连接多路复用）。
     * 若每次构建 Agent 都新建客户端，配置变更重建时旧客户端无人关闭，事件循环线程会持续泄漏。
     */
    private volatile RedisClient sharedRedisClient;

    /** tenantId → tenantCode 缓存（避免每次构建 Agent 都查库） */
    private final Map<Long, String> tenantCodes = new ConcurrentHashMap<>();

    private final ClawProperties properties;
    private final ConfigService configService;
    private final CapabilityService capabilityService;
    private final ModelFactory modelFactory;
    private final ToolRegistry toolRegistry;
    private final EmailService emailService;
    private final TenantMapper tenantMapper;
    private final SessionSummaryService sessionSummaryService;

    /**
     * 构造函数：注入核心依赖（Redis 相关字段通过 {@code @Autowired(required=false)} 单独注入）。
     */
    public AgentRegistry(ClawProperties properties, ConfigService configService,
                         CapabilityService capabilityService, ModelFactory modelFactory,
                         ToolRegistry toolRegistry, EmailService emailService,
                         TenantMapper tenantMapper, SessionSummaryService sessionSummaryService) {
        this.properties = properties;
        this.configService = configService;
        this.capabilityService = capabilityService;
        this.modelFactory = modelFactory;
        this.toolRegistry = toolRegistry;
        this.emailService = emailService;
        this.tenantMapper = tenantMapper;
        this.sessionSummaryService = sessionSummaryService;
    }

    /** Redis 配置（可选：未安装 Redis 时为 null，自动降级到 JSON 存储） */
    @Autowired(required = false)
    private RedisProperties redisProperties;

    /** Redis 可用性标志（可选：RedisOptionalConfig 未加载时为 false） */
    @Autowired(required = false)
    private Boolean redisAvailable = false;

    /**
     * 获取（或构建）用户的 Agent 实例。
     *
     * @param userId       用户ID（Agent 的 userId，格式：租户编码_自增序号）
     * @param tenantId     租户ID
     * @param presetCode   预设模板编码（可为 null，表示默认人格）
     * @param presetPrompt 预设模板人格内容（可为 null，与基础提示词叠加）
     * @return 满血版 HarnessAgent（调用侧通过 streamEvents + RuntimeContext 驱动）
     */
    public HarnessAgent getOrCreate(String userId, Long tenantId,
                                    String presetCode, String presetPrompt) {
        String cacheKey = userId + (StringUtils.hasText(presetCode) ? "#" + presetCode : "");
        userTenants.put(userId, tenantId);
        return agents.computeIfAbsent(cacheKey,
                key -> build(userId, tenantId, presetPrompt));
    }

    /** 失效单个用户的缓存（其配置被修改时调用） */
    public void invalidate(String userId) {
        agents.keySet().removeIf(key -> key.equals(userId) || key.startsWith(userId + "#"));
        log.info("Agent 缓存已失效: {}", userId);
    }

    /**
     * 监听配置变更事件，按作用域精准失效缓存：
     * GLOBAL → 全量失效；TENANT → 该租户用户失效；USER → 按归属用户ID失效。
     * <p>
     * 必须等事务提交后再失效：若在事务内同步失效，并发请求可能在提交前
     * 重建 Agent 读到旧配置；事务回滚时也会白白失效缓存。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigChanged(ConfigChangedEvent event) {
        if (ConfigService.SCOPE_PLATFORM.equals(event.getScope())) {
            agents.clear();
            log.info("全局配置变更，已清空全部 Agent 缓存");
            return;
        }
        if (ConfigService.SCOPE_TENANT.equals(event.getScope())) {
            Long tenantId = event.getTenantId();
            Iterator<Map.Entry<String, Long>> it = userTenants.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> entry = it.next();
                if (tenantId != null && tenantId.equals(entry.getValue())) {
                    invalidate(entry.getKey());
                    it.remove();
                }
            }
            log.info("租户 {} 配置变更，已失效该租户 Agent 缓存", tenantId);
            return;
        }
        if (ConfigService.SCOPE_USER.equals(event.getScope())
                && StringUtils.hasText(event.getOwnerId())) {
            invalidate(event.getOwnerId());
        }
    }

    /** 容器关闭时释放共享 Redis 客户端的事件循环线程与连接 */
    @PreDestroy
    public void destroy() {
        if (sharedRedisClient != null) {
            sharedRedisClient.shutdown();
        }
    }

    /**
     * 构建满血版 HarnessAgent（数据库三级配置驱动）。
     * <p>
     * 两阶段构建：Phase 1 集中校验所有前置条件（快速失败，不产生副作用），
     * Phase 2 执行工具注册 / MCP 挂载等昂贵操作。
     * 任何前置条件不满足时立即抛 BizException，由 AgentService.onErrorResume 转为 SSE error 事件。
     */
    private HarnessAgent build(String userId, Long tenantId, String presetPrompt) {
        // ==================== Phase 1: 前置条件校验（快速失败） ====================

        // 1. 模型提供商 + API Key（最关键的依赖，缺失则完全无法对话）
        ModelProviderConfig provider = configService.resolveCurrentProvider(tenantId, userId);
        Model chatModel = modelFactory.createModel(provider);

        // 2. 工作区路径（Agent 人格/技能/记忆的存储根目录）
        String workspaceBase = properties.getAgent().getWorkspace();
        if (!StringUtils.hasText(workspaceBase)) {
            throw new BizException(ResultCode.AGENT_ERROR,
                    "Agent 工作区路径未配置（claw.agent.workspace），请检查 application.yml");
        }
        String tenantCode = resolveTenantCode(tenantId);
        Path workspace = Paths.get(workspaceBase, tenantCode, userId).toAbsolutePath().normalize();
        // 确保工作区根目录存在且可写（提前暴露权限问题，而非运行时才发现）
        try {
            Files.createDirectories(workspace);
        } catch (Exception e) {
            throw new BizException(ResultCode.AGENT_ERROR,
                    "工作区目录创建失败: " + workspace + "（" + e.getMessage() + "），请检查磁盘权限");
        }

        // 3. 状态存储类型（仅允许 redis / json，非法值回退默认并告警）
        String storeType = configService.resolveValue(ConfigService.KEY_STATE_STORE_TYPE, tenantId, userId);
        if (StringUtils.hasText(storeType)
                && !STORE_TYPE_REDIS.equalsIgnoreCase(storeType)
                && !"json".equalsIgnoreCase(storeType)) {
            log.warn("状态存储类型 {} 非法，回退为 json", storeType);
            storeType = "json";
        }

        // 4. 权限模式（非法值回退 DEFAULT）
        String permMode = configService.resolveValue(ConfigService.KEY_PERMISSION_MODE, tenantId, userId);
        parsePermissionMode(permMode); // 仅校验，不赋值（buildPermissionContext 内部会再解析）

        // 5. 上下文压缩参数（范围校验，非法值回退默认）
        int compactTrigger = configService.resolveInt(ConfigService.KEY_COMPACTION_TRIGGER, tenantId, userId, 30);
        int compactKeep = configService.resolveInt(ConfigService.KEY_COMPACTION_KEEP, tenantId, userId, 10);
        if (compactTrigger < 5) compactTrigger = 30;
        if (compactKeep < 3) compactKeep = 10;
        if (compactKeep >= compactTrigger) compactKeep = compactTrigger - 1;

        int flushMinutes = configService.resolveInt(ConfigService.KEY_MEMORY_FLUSH_MINUTES, tenantId, userId, 10);
        if (flushMinutes < 1) flushMinutes = 10;

        log.info("前置校验通过: user={}, tenant={}, provider={}, model={}, stateStore={}, workspace={}",
                userId, tenantCode, provider.getProvider(), provider.getModelName(), storeType, workspace);

        // ==================== Phase 2: 昂贵操作（前置条件全部满足后才执行） ====================

        // 6. 动态工具注册：从 ToolRegistry 获取已启用的工具集并实例化
        Toolkit toolkit = new Toolkit();
        
        // 获取已启用的工具集代码列表
        Set<String> enabledToolCodes = toolRegistry.getEnabledToolCodes();
        log.info("用户 {} 已启用 {} 个工具集", userId, enabledToolCodes.size());
        
        // 需要特殊构造参数的工具集，跳过通用反射注册，后面手动创建
        Set<String> manualToolCodes = Set.of(
                CapabilityService.TOOL_NOTE,   // NoteTools(workspace)
                ToolCodes.EMAIL_TOOLS,          // EmailTools(emailService)
                ToolCodes.KNOWLEDGE_SEARCH      // KnowledgeSearchTools(workspace)
        );

        // 遍历并注册每个工具集（跳过需要特殊构造的）
        for (String toolCode : enabledToolCodes) {
            if (manualToolCodes.contains(toolCode)) {
                continue;
            }
            try {
                Object toolInstance = toolRegistry.getOrCreateInstance(toolCode);
                toolkit.registerTool(toolInstance);
                log.debug("已注册工具集: user={}, code={}", userId, toolCode);
            } catch (Exception e) {
                log.error("注册工具集失败: user={}, code={}", userId, toolCode, e);
            }
        }
        
        // 特殊处理：NoteTools 需要 workspace 参数，无法通过无参构造创建
        if (enabledToolCodes.contains(CapabilityService.TOOL_NOTE)) {
            toolkit.registerTool(new NoteTools(workspace.toString()));
            log.debug("已注册 NoteTools: user={}", userId);
        }
        
        // 特殊处理：EmailTools 需要 EmailService 依赖
        if (enabledToolCodes.contains(ToolCodes.EMAIL_TOOLS)) {
            toolkit.registerTool(new EmailTools(emailService));
            log.debug("已注册 EmailTools: user={}", userId);
        }

        // 特殊处理：KnowledgeSearchTools 需要 workspace 参数，扫描 knowledge/ 目录
        if (enabledToolCodes.contains(ToolCodes.KNOWLEDGE_SEARCH)) {
            toolkit.registerTool(new KnowledgeSearchTools(workspace.toString()));
            log.debug("已注册 KnowledgeSearchTools: user={}", userId);
        }

        // 5. MCP 服务器：就近解析三级作用域登记且启用的服务器，挂载其暴露的工具；
        //    单个服务器注册失败仅告警（Registrar 内部捕获），不阻断 Agent 构建
        Map<String, McpServerConfig> mcpServers = capabilityService.resolveMcpServers(tenantId, userId);
        if (!mcpServers.isEmpty()) {
            McpServerRegistrar.register(toolkit, mcpServers);
            log.info("已挂载 MCP 服务器: user={}, servers={}", userId, mcpServers.keySet());
        }

        // 6. 加载子 Agent 定义：编程式注册 + 提示词感知双通道
        SubagentLoadResult subagentResult = loadSubagents(workspace, provider.getProvider());
        // 7. 跨会话记忆：注入近期会话摘要到系统提示词，让 Agent 知道用户最近在做什么
        String summarySuffix = buildSummarySuffix(userId);
        String fullSysPrompt = composeSysPrompt(presetPrompt) + subagentResult.promptSuffix + summarySuffix;

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(properties.getAgent().getName())
                .sysPrompt(fullSysPrompt)  // 使用包含子 Agent 信息的系统提示词
                .model(chatModel)
                .workspace(workspace)
                .toolkit(toolkit)  // 传入已注册工具的 toolkit
                // 上下文压缩：超过触发条数触发，保留最近 N 条，其余蒸馏为摘要
                .compaction(CompactionConfig.builder()
                        .triggerMessages(compactTrigger)
                        .keepMessages(compactKeep)
                        .build())
                // 双层记忆：flush 节流，控制辅助 LLM 调用成本
                .memory(MemoryConfig.builder()
                        .flushTrigger(MemoryConfig.FlushTrigger.throttled(Duration.ofMinutes(flushMinutes)))
                        .build())
                // 超大工具结果落盘、上下文只留占位符，防止上下文爆炸
                .toolResultEviction(ToolResultEvictionConfig.defaults())
                // 技能自学习：agent 可起草新技能（草稿 → 审核两步流程）
                .enableSkillManageTool(SkillManageConfig.defaults())
                // 后台周期整理：30 天未用标为 stale，90 天归档
                .enableSkillCurator(SkillCuratorConfig.builder()
                        .intervalHours(24 * 7)
                        .staleAfterDays(30)
                        .archiveAfterDays(90)
                        .build())
                // 计划模式：复杂任务先只读规划、用户确认后再执行
                .enablePlanMode()
                // 权限系统：模式来自数据库配置，危险路径仍由框架内置检查强制拦截
                .permissionContext(buildPermissionContext(permMode))
                // 安全护栏（最先执行：拦截 Prompt Injection + 输出脱敏）
                .middleware(new GuardrailsMiddleware())
                // 工具输入安全检查（第二执行：拦截危险工具调用）
                .middleware(new ToolCheckMiddleware())
                // 执行链路追踪 + 性能监控（自定义 middleware 在 Harness 内置 middleware 之前执行）
                .middleware(new AgentTraceMiddleware())
                .middleware(new PerformanceMiddleware())
                // 工具执行重试：工具调用失败时自动重试（#2829 修复：此前未配置导致工具失败不重试）
                .toolExecutionConfig(TOOL_EXECUTION_CONFIG)
                // 状态存储：分布式部署用 Redis，单机开发可切 json
                .stateStore(buildStateStore(storeType))
                // 自定义工具集（含 MCP 挂载的工具）
                .toolkit(toolkit);

        // 注册子 Agent 声明（编程式编排：主 Agent 可通过 agent_spawn/agent_send 委派任务）
        for (SubagentDeclaration decl : subagentResult.declarations) {
            builder.subagent(decl);
            log.debug("已注册子 Agent: name={}, workspaceMode={}", decl.getName(), decl.getWorkspaceMode());
        }

        // 7. 内置工具开关：禁用项交由 Builder 的 disable* 接口，技能禁用名单按技能名过滤
        if (!capabilityService.isToolEnabled(CapabilityService.TOOL_FILESYSTEM, tenantId, userId)) {
            builder.disableFilesystemTools();
        }
        if (!capabilityService.isToolEnabled(CapabilityService.TOOL_SHELL, tenantId, userId)) {
            builder.disableShellTool();
        }
        if (!capabilityService.isToolEnabled(CapabilityService.TOOL_MEMORY, tenantId, userId)) {
            builder.disableMemoryTools();
        }
        List<String> disabledSkills = capabilityService.resolveDisabledSkills(tenantId, userId);
        if (!disabledSkills.isEmpty()) {
            builder.disableSkills(disabledSkills.toArray(new String[0]));
            log.info("已禁用技能: user={}, skills={}", userId, disabledSkills);
        }

        return builder.build();
    }

    /** 组合系统提示词：预设人格模板（若有）叠加在基础提示词之前 */
    private String composeSysPrompt(String presetPrompt) {
        if (!StringUtils.hasText(presetPrompt)) {
            return BASE_SYS_PROMPT;
        }
        return presetPrompt + "\n\n---\n\n" + BASE_SYS_PROMPT;
    }

    /**
     * 构建跨会话记忆后缀：取用户最近几个会话的摘要拼入系统提示词。
     * <p>
     * 无摘要时返回空字符串，不影响原有提示词；有摘要时追加「近期会话摘要」段落，
     * 让 Agent 知道用户最近在做什么，实现跨会话上下文延续。
     *
     * @param username 用户名（Agent 的 userId 实际是 username）
     * @return 摘要后缀片段（空字符串或 Markdown 段落）
     */
    private String buildSummarySuffix(String username) {
        try {
            List<String> summaries = sessionSummaryService.getRecentSummaries(username);
            if (summaries.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("\n\n## 近期会话摘要\n");
            sb.append("以下是你与该用户最近的对话摘要，请参考这些上下文理解用户的当前需求：\n");
            for (int i = 0; i < summaries.size(); i++) {
                sb.append("- ").append(summaries.get(i)).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取会话摘要失败（不影响 Agent 构建）: user={}, error={}", username, e.getMessage());
            return "";
        }
    }

    /**
     * 权限上下文：模式来自数据库（DEFAULT / ACCEPT_EDITS / EXPLORE / BYPASS / DONT_ASK），
     * 只读类工具显式放行以免打扰用户，危险操作强制人工确认。
     * <p>
     * write_file 行为随 permission_mode 动态调整：
     * - ACCEPT_EDITS / BYPASS：自动放行（流水线自动存档等场景无需 HITL）
     * - DEFAULT / EXPLORE：强制 HITL 确认
     * - DONT_ASK：拒绝写入（无人值守模式）
     */
    private PermissionContextState buildPermissionContext(String modeValue) {
        PermissionMode mode = parsePermissionMode(modeValue);
        PermissionContextState.Builder builder = PermissionContextState.builder().mode(mode);
        // 只读 / 安全工具：自动放行
        for (String tool : new String[]{"read_file", "grep", "glob", "ls",
                "memory_search", "memory_get", "todo_write"}) {
            builder.addAllowRule(tool,
                    new PermissionRule(tool, null, PermissionBehavior.ALLOW, RULE_SOURCE_POLICY));
        }
        // write_file 按 permission_mode 动态放行：
        // ACCEPT_EDITS / BYPASS → 自动放行；DEFAULT / EXPLORE → HITL；DONT_ASK → 拒绝
        if (mode == PermissionMode.ACCEPT_EDITS || mode == PermissionMode.BYPASS) {
            builder.addAllowRule("write_file",
                    new PermissionRule("write_file", null, PermissionBehavior.ALLOW, RULE_SOURCE_POLICY));
        } else if (mode == PermissionMode.DONT_ASK) {
            builder.addAskRule("write_file",
                    new PermissionRule("write_file", null, PermissionBehavior.DENY, RULE_SOURCE_POLICY));
        } else {
            // DEFAULT / EXPLORE：文件写入需 HITL 确认，防止 Agent 误改源码
            builder.addAskRule("write_file",
                    new PermissionRule("write_file", null, PermissionBehavior.ASK, RULE_SOURCE_POLICY));
        }
        // delete_note 与 write_file 同属常规工作流操作，跟随 permission_mode 动态放行
        if (mode == PermissionMode.ACCEPT_EDITS || mode == PermissionMode.BYPASS) {
            builder.addAllowRule("delete_note",
                    new PermissionRule("delete_note", null, PermissionBehavior.ALLOW, RULE_SOURCE_POLICY));
        } else if (mode == PermissionMode.DONT_ASK) {
            builder.addAskRule("delete_note",
                    new PermissionRule("delete_note", null, PermissionBehavior.DENY, RULE_SOURCE_POLICY));
        } else {
            builder.addAskRule("delete_note",
                    new PermissionRule("delete_note", null, PermissionBehavior.ASK, RULE_SOURCE_POLICY));
        }
        // 真正危险操作：无论模式如何都强制人工确认（不可逆破坏性操作）
        for (String tool : new String[]{"dangerous_delete", "drop_table", "force_push"}) {
            builder.addAskRule(tool,
                    new PermissionRule(tool, null, PermissionBehavior.ASK, RULE_SOURCE_POLICY));
        }
        return builder.build();
    }

    /** 解析权限模式，非法值回退 DEFAULT 并告警 */
    private PermissionMode parsePermissionMode(String modeValue) {
        String value = StringUtils.hasText(modeValue) ? modeValue.trim() : DEFAULT_PERMISSION_MODE;
        try {
            return PermissionMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("权限模式 {} 非法，回退为 {}", value, DEFAULT_PERMISSION_MODE);
            return PermissionMode.DEFAULT;
        }
    }

    /**
     * 按数据库配置构建状态存储。
     * <p>
     * redis：RedisAgentStateStore（Lettuce 客户端，多副本部署任意节点可恢复会话）；
     * 其它：JsonFileAgentStateStore（本地文件，仅适合单机开发）。
     */
    private AgentStateStore buildStateStore(String storeType) {
        // Redis 不可用时强制降级到本地 JSON 存储，无论数据库配置为何值
        if (STORE_TYPE_REDIS.equalsIgnoreCase(storeType) && Boolean.TRUE.equals(redisAvailable) && redisProperties != null) {
            return RedisAgentStateStore.builder()
                    .lettuceClient(redisClient())
                    .keyPrefix(REDIS_KEY_PREFIX)
                    .build();
        }
        if (STORE_TYPE_REDIS.equalsIgnoreCase(storeType)) {
            log.warn("Agent 状态存储配置为 redis，但 Redis 不可用，自动降级为本地 JSON 文件存储（仅限单机开发）");
        } else if (StringUtils.hasText(storeType)) {
            log.warn("Agent 状态存储使用本地 JSON 文件，仅限单机开发；生产请配置 state_store_type=redis");
        }
        return new JsonFileAgentStateStore();
    }

    /** 获取共享 Redis 客户端（双重检查懒加载，全部 Agent 状态存储复用） */
    private RedisClient redisClient() {
        RedisClient local = sharedRedisClient;
        if (local == null) {
            synchronized (this) {
                local = sharedRedisClient;
                if (local == null) {
                    local = buildRedisClient();
                    sharedRedisClient = local;
                }
            }
        }
        return local;
    }

    /** 从 Spring Redis 配置构建 Lettuce RedisClient（复用同一套连接参数） */
    private RedisClient buildRedisClient() {
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisProperties.getHost())
                .withPort(redisProperties.getPort())
                .withDatabase(redisProperties.getDatabase());
        if (StringUtils.hasText(redisProperties.getPassword())) {
            uriBuilder.withPassword(redisProperties.getPassword().toCharArray());
        }
        return RedisClient.create(uriBuilder.build());
    }

    /**
     * 子 Agent 加载结果：编程式声明列表 + 提示词后缀。
     * <p>
     * declarations 用于 HarnessAgent.builder().subagent() 注册，实现编程式编排；
     * promptSuffix 注入主 Agent 系统提示词，让其感知有哪些子 Agent 可委派。
     */
    private record SubagentLoadResult(List<SubagentDeclaration> declarations, String promptSuffix) {}

    /**
     * 加载工作区子 Agent 定义：同时生成编程式声明和提示词感知文本。
     * <p>
     * 扫描 workspace/subagents/*.md，每个文件生成一个 SubagentDeclaration（编程式编排），
     * 同时拼接提示词后缀让主 Agent 知道有哪些子 Agent 可用（双通道保障）。
     *
     * @param workspace    用户工作区路径
     * @param modelName    当前模型提供商（子 Agent 复用主 Agent 模型）
     * @return 声明列表 + 提示词后缀
     */
    private SubagentLoadResult loadSubagents(Path workspace, String modelName) {
        Path subagentsDir = workspace.resolve("subagents");
        if (!Files.exists(subagentsDir)) {
            log.debug("子 Agent 目录不存在: {}", subagentsDir);
            return new SubagentLoadResult(List.of(), "");
        }

        List<SubagentDeclaration> declarations = new ArrayList<>();
        List<String> subagentInfos = new ArrayList<>();

        // 子 Agent 上限 3 个：超过会挤占主 Agent 上下文窗口，且 Token 消耗线性增长
        final int MAX_SUBAGENTS = 3;
        try (var stream = Files.list(subagentsDir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                  .limit(MAX_SUBAGENTS)
                  .forEach(path -> {
                      try {
                          String content = Files.readString(path);
                          String name = path.getFileName().toString().replace(".md", "");
                          String description = extractRoleSection(content);

                          // 编程式声明：子 Agent 复用主 Agent 模型，工作区隔离，最多 25 轮迭代
                          SubagentDeclaration decl = SubagentDeclaration.builder()
                                  .name(name)
                                  .description(description)
                                  .inlineAgentsBody(content)
                                  .workspaceMode(WorkspaceMode.ISOLATED)
                                  .model(modelName)
                                  .maxIters(25)
                                  .persistSession(true)
                                  .inheritParentPermissions(true)
                                  .exposeToUser(true)
                                  .build();
                          declarations.add(decl);

                          subagentInfos.add(String.format("- **%s**: %s", name, description));
                          log.debug("已加载子 Agent: {}, 角色: {}", name, description);
                      } catch (Exception e) {
                          log.error("读取子 Agent 文件失败: {}", path, e);
                      }
                  });
            // 超出上限时记录告警
            long total;
            try (var countStream = Files.list(subagentsDir)) {
                total = countStream.filter(p -> p.toString().endsWith(".md")).count();
            }
            if (total > MAX_SUBAGENTS) {
                log.warn("子 Agent 数量 {} 超过上限 {}，仅加载前 {} 个", total, MAX_SUBAGENTS, MAX_SUBAGENTS);
            }
        } catch (Exception e) {
            log.error("扫描子 Agent 目录失败: {}", subagentsDir, e);
            return new SubagentLoadResult(List.of(), "");
        }

        if (declarations.isEmpty()) {
            return new SubagentLoadResult(List.of(), "");
        }

        // 拼接提示词后缀（让主 Agent 感知子 Agent 存在，与编程式注册互补）
        StringBuilder prompt = new StringBuilder("\n\n## 可用的子 Agent\n\n");
        prompt.append("你可以将复杂任务拆解并委派给以下专门的子 Agent 执行：\n\n");
        prompt.append(String.join("\n", subagentInfos));
        prompt.append("\n\n**委派规则**:\n");
        prompt.append("1. 当用户请求涉及多步骤协作时，按需创建子 Agent\n");
        prompt.append("2. 每个子 Agent 有独立的工作区和记忆，结果会自动汇总到你这里\n");
        prompt.append("3. 子 Agent 执行完成后，你需要整合它们的输出并返回给用户\n");

        return new SubagentLoadResult(declarations, prompt.toString());
    }

    /**
     * 从 Markdown 内容中提取 Role 章节。
     *
     * @param content Markdown 全文
     * @return Role 描述，如果未找到则返回"专业助手"
     */
    private String extractRoleSection(String content) {
        // 匹配 "## Role" 章节，提取到下一个 "##" 之前的内容
        Pattern pattern = Pattern.compile("##\\s+Role\\s*\n(.*?)(?=\n##\\s|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String role = matcher.group(1).trim();
            // 只取第一行作为简要描述
            int firstLineEnd = role.indexOf('\n');
            if (firstLineEnd > 0) {
                role = role.substring(0, firstLineEnd).trim();
            }
            // 限制长度，避免 prompt 过长
            if (role.length() > 100) {
                role = role.substring(0, 97) + "...";
            }
            return role;
        }
        return "专业助手";
    }

    /**
     * 解析租户编码：优先从缓存取，未命中则查库并缓存。
     * <p>
     * 工作区目录使用编码而非数字ID，可读性更好（如 {@code workspace/acme/admin} 而非 {@code workspace/1/admin}）。
     * tenantId 为 null 时回退 "0"（平台级默认）。
     *
     * @param tenantId 租户ID
     * @return 租户编码（不会返回 null）
     */
    private String resolveTenantCode(Long tenantId) {
        if (tenantId == null) {
            return "0";
        }
        return tenantCodes.computeIfAbsent(tenantId, id -> {
            Tenant tenant = tenantMapper.selectById(id);
            if (tenant != null && StringUtils.hasText(tenant.getTenantCode())) {
                return tenant.getTenantCode();
            }
            log.warn("租户 {} 不存在或无编码，工作区目录回退为数字ID", id);
            return String.valueOf(id);
        });
    }
}
