package com.claw.agent.config.agent;

import com.claw.agent.common.BizException;
import com.claw.agent.common.ResultCode;
import com.claw.agent.model.ModelProviderConfig;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import io.agentscope.extensions.model.anthropic.formatter.AnthropicChatFormatter;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.gemini.GeminiChatModel;
import io.agentscope.extensions.model.gemini.formatter.GeminiChatFormatter;
import io.agentscope.extensions.model.ollama.OllamaChatModel;
import io.agentscope.extensions.model.ollama.formatter.OllamaChatFormatter;
import io.agentscope.extensions.model.ollama.options.OllamaOptions;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型工厂：按数据库配置（model_provider_config，三级作用域解析后的结果）构建 Chat Model。
 * <p>
 * 支持七种提供商：
 * <ul>
 *   <li>dashscope  —— 阿里云通义千问（默认）</li>
 *   <li>deepseek   —— DeepSeek 官方（OpenAI 兼容协议，默认指向平台端点）</li>
 *   <li>volcengine —— 火山方舟/豆包（OpenAI 兼容协议，默认指向 ark.cn-beijing.volces.com）</li>
 *   <li>openai     —— OpenAI 及任意 OpenAI 兼容端点（Kimi / vLLM 等，通过 base-url 指向）</li>
 *   <li>ollama     —— 本地 Ollama</li>
 *   <li>anthropic  —— Anthropic Claude（Claude 3.5/3.7/4 系列）</li>
 *   <li>gemini     —— Google Gemini（Gemini 2.0/2.5 系列）</li>
 * </ul>
 * 阿里规约：模型构建逻辑集中收敛到工厂类，禁止在业务代码中散落构建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelFactory {

    /** 提供商常量 */
    private static final String PROVIDER_DASHSCOPE = "dashscope";
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final String PROVIDER_VOLCENGINE = "volcengine";
    private static final String PROVIDER_OPENAI = "openai";
    private static final String PROVIDER_OLLAMA = "ollama";
    private static final String PROVIDER_ANTHROPIC = "anthropic";
    private static final String PROVIDER_GEMINI = "gemini";

    /** Ollama 默认端点 */
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";
    /** DeepSeek 官方 OpenAI 兼容端点 */
    private static final String DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    /** 火山方舟（豆包）通用模型 OpenAI 兼容端点 */
    private static final String DEFAULT_VOLCENGINE_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    /** Anthropic Claude 默认端点 */
    private static final String DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com";
    /** Google Gemini 默认端点 */
    private static final String DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";

    /** 各提供商默认模型名 */
    private static final String DEFAULT_DASHSCOPE_MODEL = "qwen-plus";
    private static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-chat";
    private static final String DEFAULT_VOLCENGINE_MODEL = "doubao-seed-2-1-pro-260628";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4.1-mini";
    private static final String DEFAULT_OLLAMA_MODEL = "qwen2.5:7b";
    private static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-20250514";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.0-flash";

    /**
     * 模型容错配置：最大重试 3 次，指数退避（1s → 2s → 4s），超时 120s。
     * 覆盖网络抖动、模型服务短暂不可用等常见故障场景。
     */
    private static final ExecutionConfig MODEL_EXECUTION_CONFIG = ExecutionConfig.builder()
            .maxAttempts(3)
            .timeout(Duration.ofSeconds(120))
            .initialBackoff(Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(8))
            .backoffMultiplier(2.0)
            .build();

    /** 将容错配置包装为 GenerateOptions，供所有模型 Builder 复用 */
    private static final GenerateOptions MODEL_GENERATE_OPTIONS = GenerateOptions.builder()
            .executionConfig(MODEL_EXECUTION_CONFIG)
            .build();

    /** 模型实例缓存（配置指纹 → Model），相同提供商+模型参数复用实例，避免重复创建 */
    private final Map<String, Model> modelCache = new ConcurrentHashMap<>();

    /**
     * 按提供商配置构建 Chat Model（流式输出 + 自动重试）。
     * <p>
     * 相同提供商配置（provider + model + baseUrl + apiKey 指纹）复用缓存实例，
     * 避免每次 Agent 构建都创建新的 HTTP 客户端与连接池。
     * 已集成：
     * <ul>
     *   <li>模型容错：最大重试 3 次，指数退避，超时 120s</li>
     *   <li>Token 追踪：由 AgentService 通过 ModelCallEndEvent 自动提取 ChatUsage 落库</li>
     * </ul>
     *
     * @param cfg 三级作用域解析后的提供商配置（apiKey 已由 ConfigService 解密）
     * @return AgentScope Model 实例
     */
    public Model createModel(ModelProviderConfig cfg) {
        String cacheKey = buildModelCacheKey(cfg);
        return modelCache.computeIfAbsent(cacheKey, k -> doCreateModel(cfg));
    }

    /** 实际构建模型的内部方法（缓存未命中时调用） */
    private Model doCreateModel(ModelProviderConfig cfg) {
        String provider = cfg.getProvider() == null ? PROVIDER_DASHSCOPE
                : cfg.getProvider().toLowerCase(Locale.ROOT);
        log.info("构建 Chat Model，提供商: {}, 模型: {}", provider, cfg.getModelName());
        
        return switch (provider) {
            case PROVIDER_DEEPSEEK -> buildDeepSeek(cfg);
            case PROVIDER_VOLCENGINE -> buildVolcengine(cfg);
            case PROVIDER_OPENAI -> buildOpenAi(cfg);
            case PROVIDER_OLLAMA -> buildOllama(cfg);
            case PROVIDER_ANTHROPIC -> buildAnthropic(cfg);
            case PROVIDER_GEMINI -> buildGemini(cfg);
            default -> buildDashScope(cfg);
        };
    }

    /**
     * 构建模型缓存键：基于 provider + modelName + baseUrl + apiKey 指纹。
     * <p>
     * apiKey 取 hashCode 而非明文，避免缓存键过长；配置变更（含密钥轮换）自然产生不同键，
     * 旧缓存条目由 Agent 缓存失效机制间接清理。
     */
    private String buildModelCacheKey(ModelProviderConfig cfg) {
        String provider = cfg.getProvider() == null ? PROVIDER_DASHSCOPE
                : cfg.getProvider().toLowerCase(Locale.ROOT);
        String model = fallback(cfg.getModelName(), switch (provider) {
            case PROVIDER_DEEPSEEK -> DEFAULT_DEEPSEEK_MODEL;
            case PROVIDER_VOLCENGINE -> DEFAULT_VOLCENGINE_MODEL;
            case PROVIDER_OPENAI -> DEFAULT_OPENAI_MODEL;
            case PROVIDER_OLLAMA -> DEFAULT_OLLAMA_MODEL;
            case PROVIDER_ANTHROPIC -> DEFAULT_ANTHROPIC_MODEL;
            case PROVIDER_GEMINI -> DEFAULT_GEMINI_MODEL;
            default -> DEFAULT_DASHSCOPE_MODEL;
        });
        String baseUrl = cfg.getBaseUrl() == null ? "" : cfg.getBaseUrl();
        int keyHash = cfg.getApiKey() == null ? 0 : cfg.getApiKey().hashCode();
        return provider + ":" + model + ":" + baseUrl + ":" + keyHash;
    }

    /**
     * 构建 DeepSeek 模型：走 OpenAI 兼容协议，独立提供商分支以便锁定官方端点与默认模型，
     * 避免用户借 openai 提供商手填 base-url 时出错。
     */
    private Model buildDeepSeek(ModelProviderConfig cfg) {
        requireApiKey(cfg.getApiKey(), PROVIDER_DEEPSEEK, "DEEPSEEK_API_KEY");
        String baseUrl = StringUtils.hasText(cfg.getBaseUrl())
                ? cfg.getBaseUrl() : DEFAULT_DEEPSEEK_BASE_URL;
        return OpenAIChatModel.builder()
                .apiKey(cfg.getApiKey())
                .baseUrl(baseUrl)
                .modelName(fallback(cfg.getModelName(), DEFAULT_DEEPSEEK_MODEL))
                .stream(true)
                .formatter(new OpenAIChatFormatter())
                .generateOptions(MODEL_GENERATE_OPTIONS)
                .build();
    }

    /**
     * 构建火山方舟（豆包）模型：走 OpenAI 兼容协议，默认指向 ark.cn-beijing.volces.com/api/v3。
     * <p>
     * model 参数可填 Endpoint ID（如 ep-20241220174930-xxxxx）或模型名（如 doubao-seed-2-1-pro-260628）。
     */
    private Model buildVolcengine(ModelProviderConfig cfg) {
        requireApiKey(cfg.getApiKey(), PROVIDER_VOLCENGINE, "ARK_API_KEY");
        String baseUrl = StringUtils.hasText(cfg.getBaseUrl())
                ? cfg.getBaseUrl() : DEFAULT_VOLCENGINE_BASE_URL;
        return OpenAIChatModel.builder()
                .apiKey(cfg.getApiKey())
                .baseUrl(baseUrl)
                .modelName(fallback(cfg.getModelName(), DEFAULT_VOLCENGINE_MODEL))
                .stream(true)
                .formatter(new OpenAIChatFormatter())
                .generateOptions(MODEL_GENERATE_OPTIONS)
                .build();
    }

    /** 构建 DashScope（通义千问）模型 */
    private Model buildDashScope(ModelProviderConfig cfg) {
        requireApiKey(cfg.getApiKey(), PROVIDER_DASHSCOPE, "DASHSCOPE_API_KEY");
        return DashScopeChatModel.builder()
                .apiKey(cfg.getApiKey())
                .modelName(fallback(cfg.getModelName(), DEFAULT_DASHSCOPE_MODEL))
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(MODEL_GENERATE_OPTIONS)
                .build();
    }

    /** 构建 OpenAI / OpenAI 兼容协议模型（baseUrl 为空时走官方端点） */
    private Model buildOpenAi(ModelProviderConfig cfg) {
        requireApiKey(cfg.getApiKey(), PROVIDER_OPENAI, "OPENAI_API_KEY");
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(cfg.getApiKey())
                .modelName(fallback(cfg.getModelName(), DEFAULT_OPENAI_MODEL))
                .stream(true)
                .formatter(new OpenAIChatFormatter())
                .generateOptions(MODEL_GENERATE_OPTIONS);
        if (StringUtils.hasText(cfg.getBaseUrl())) {
            builder.baseUrl(cfg.getBaseUrl());
        } else if (StringUtils.hasText(System.getenv("OPENAI_BASE_URL"))) {
            builder.baseUrl(System.getenv("OPENAI_BASE_URL"));
        }
        return builder.build();
    }

    /** 构建本地 Ollama 模型（无需 API key） */
    private Model buildOllama(ModelProviderConfig cfg) {
        String baseUrl = StringUtils.hasText(cfg.getBaseUrl())
                ? cfg.getBaseUrl() : DEFAULT_OLLAMA_BASE_URL;
        // Ollama 使用独立的 OllamaOptions，其中包含 executionConfig
        OllamaOptions ollamaOptions = OllamaOptions.builder()
                .executionConfig(MODEL_EXECUTION_CONFIG)
                .build();
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(fallback(cfg.getModelName(), DEFAULT_OLLAMA_MODEL))
                .formatter(new OllamaChatFormatter())
                .defaultOptions(ollamaOptions)
                .build();
    }

    /**
     * 构建 Anthropic Claude 模型。
     * <p>
     * 支持 Claude 3.5/3.7/4 系列，默认 claude-sonnet-4-20250514。
     * API Key 环境变量：ANTHROPIC_API_KEY
     */
    private Model buildAnthropic(ModelProviderConfig cfg) {
        requireApiKey(cfg.getApiKey(), PROVIDER_ANTHROPIC, "ANTHROPIC_API_KEY");
        AnthropicChatModel.Builder builder = AnthropicChatModel.builder()
                .apiKey(cfg.getApiKey())
                .modelName(fallback(cfg.getModelName(), DEFAULT_ANTHROPIC_MODEL))
                .stream(true)
                .formatter(new AnthropicChatFormatter())
                .defaultOptions(MODEL_GENERATE_OPTIONS);
        if (StringUtils.hasText(cfg.getBaseUrl())) {
            builder.baseUrl(cfg.getBaseUrl());
        }
        return builder.build();
    }

    /**
     * 构建 Google Gemini 模型。
     * <p>
     * 支持 Gemini 2.0/2.5 系列，默认 gemini-2.0-flash。
     * API Key 环境变量：GEMINI_API_KEY
     */
    private Model buildGemini(ModelProviderConfig cfg) {
        requireApiKey(cfg.getApiKey(), PROVIDER_GEMINI, "GEMINI_API_KEY");
        GeminiChatModel.Builder builder = GeminiChatModel.builder()
                .apiKey(cfg.getApiKey())
                .modelName(fallback(cfg.getModelName(), DEFAULT_GEMINI_MODEL))
                .formatter(new GeminiChatFormatter())
                .defaultOptions(MODEL_GENERATE_OPTIONS);
        if (StringUtils.hasText(cfg.getBaseUrl())) {
            builder.baseUrl(cfg.getBaseUrl());
        }
        return builder.build();
    }

    /** API key 缺失时快速失败，给出可操作的提示 */
    private void requireApiKey(String apiKey, String provider, String envName) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(ResultCode.AGENT_ERROR,
                    "模型提供商 " + provider + " 未配置 API key，请在系统管理→模型提供商中配置，"
                            + "或设置环境变量 " + envName);
        }
    }

    /** 空值兜底 */
    private String fallback(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
