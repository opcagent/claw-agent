package com.claw.agent.controller.agent;

import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 模型发现控制器：提供各提供商支持的模型列表。
 * <p>
 * 用于前端动态展示可用模型，无需硬编码模型列表。
 * 通过模拟各提供商的模型列表，提供类似 AgentScope Credential.listModels() 的功能。
 */
@Slf4j
@Tag(name = "模型发现", description = "模型提供商支持的模型列表")
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    /**
     * 获取指定提供商支持的模型列表。
     *
     * @param provider 模型提供商（dashscope/openai/deepseek/ollama/anthropic/gemini/volcengine）
     * @return 模型列表响应
     */
    @Operation(summary = "模型列表", description = "获取指定提供商支持的模型列表")
    @GetMapping("/list")
    public Mono<Result<ListModelResponse>> listModels(@RequestParam String provider) {
        return ReactiveSupport.call(user -> {
            List<ModelCardDto> models = getModelsForProvider(provider);
            return new ListModelResponse(models, provider);
        });
    }

    /**
     * 根据提供商获取模型列表。
     *
     * @param provider 模型提供商
     * @return 模型卡片列表
     */
    private List<ModelCardDto> getModelsForProvider(String provider) {
        switch (provider.toLowerCase()) {
            case "dashscope":
                return List.of(
                        new ModelCardDto("qwen-max", "通义千问-max", 32768),
                        new ModelCardDto("qwen-plus", "通义千问-plus", 128000),
                        new ModelCardDto("qwen-turbo", "通义千问-turbo", 128000),
                        new ModelCardDto("qwen3", "通义千问3", 128000),
                        new ModelCardDto("qwen3.5", "通义千问3.5", 128000),
                        new ModelCardDto("qwen3.5-max", "通义千问3.5-max", 32768),
                        new ModelCardDto("qwen3.5-plus", "通义千问3.5-plus", 128000),
                        new ModelCardDto("qwen2.5-72b-instruct", "通义千问2.5-72B", 128000),
                        new ModelCardDto("qwen2.5-7b-instruct", "通义千问2.5-7B", 32768),
                        new ModelCardDto("qwen2.5-32b-instruct", "通义千问2.5-32B", 32768),
                        new ModelCardDto("qwen2.5-math-72b-instruct", "通义千问数学-72B", 32768),
                        new ModelCardDto("qwen2.5-coder-7b-instruct", "通义千问代码-7B", 32768),
                        new ModelCardDto("qwen-vl-max", "通义万相-max", 32768),
                        new ModelCardDto("qwen-vl-plus", "通义万相-plus", 32768),
                        new ModelCardDto("qwen-audio-turbo", "通义听悟-语音", 64000),
                        new ModelCardDto("qwen2-vl-72b-instruct", "通义万相2-VL-72B", 32768),
                        new ModelCardDto("qwen-long", "通义千问-long", 1000000)
                );
            case "openai":
                return List.of(
                        new ModelCardDto("gpt-4o", "GPT-4 Omni", 128000),
                        new ModelCardDto("gpt-4o-2024-08-06", "GPT-4 Omni (2024-08-06)", 128000),
                        new ModelCardDto("gpt-4o-mini", "GPT-4 Omni Mini", 128000),
                        new ModelCardDto("gpt-4o-mini-2024-07-18", "GPT-4 Omni Mini (2024-07-18)", 128000),
                        new ModelCardDto("gpt-4-turbo", "GPT-4 Turbo", 128000),
                        new ModelCardDto("gpt-4-turbo-2024-04-09", "GPT-4 Turbo (2024-04-09)", 128000),
                        new ModelCardDto("gpt-3.5-turbo", "GPT-3.5 Turbo", 16385),
                        new ModelCardDto("gpt-4", "GPT-4", 8192),
                        new ModelCardDto("gpt-4-32k", "GPT-4 32K", 32768),
                        new ModelCardDto("o1-preview", "GPT-o1 Preview", 128000),
                        new ModelCardDto("o1-mini", "GPT-o1 Mini", 128000),
                        new ModelCardDto("o1", "GPT-o1", 128000)
                );
            case "deepseek":
                return List.of(
                        new ModelCardDto("deepseek-v4-flash", "DeepSeek V4 Flash", 128000),
                        new ModelCardDto("deepseek-v4-pro", "DeepSeek V4 Pro", 128000),
                        new ModelCardDto("deepseek-v4-flash-vision-exp", "DeepSeek V4 Flash Vision Exp", 128000)
                );
            case "ollama":
                return List.of(
                        new ModelCardDto("llama3.3", "Llama3.3", 128000),
                        new ModelCardDto("llama3.2", "Llama3.2", 128000),
                        new ModelCardDto("llama3.2:3b", "Llama3.2 3B", 8192),
                        new ModelCardDto("llama3.2:1b", "Llama3.2 1B", 4096),
                        new ModelCardDto("llama3.1", "Llama3.1", 128000),
                        new ModelCardDto("llama3.1:8b", "Llama3.1 8B", 128000),
                        new ModelCardDto("llama3.1:70b", "Llama3.1 70B", 128000),
                        new ModelCardDto("mistral-nemo", "Mistral Nemo", 128000),
                        new ModelCardDto("mistral-large", "Mistral Large", 128000),
                        new ModelCardDto("mistral:7b", "Mistral 7B", 8192),
                        new ModelCardDto("mixtral:8x7b", "Mixtral 8x7B", 32768),
                        new ModelCardDto("phi3:medium", "Phi3 Medium", 128000),
                        new ModelCardDto("phi3:mini", "Phi3 Mini", 128000),
                        new ModelCardDto("gemma2:9b", "Gemma2 9B", 8192),
                        new ModelCardDto("gemma2:27b", "Gemma2 27B", 8192),
                        new ModelCardDto("qwen2.5:7b", "Qwen2.5 7B", 32768),
                        new ModelCardDto("qwen2:7b", "Qwen2 7B", 32768),
                        new ModelCardDto("yi:9b", "Yi 9B", 32768),
                        new ModelCardDto("yi:34b", "Yi 34B", 32768),
                        new ModelCardDto("command-r-plus", "Command R+", 128000),
                        new ModelCardDto("dbrx:instruct", "DBRX Instruct", 32768),
                        new ModelCardDto("nous-hermes2:34b", "Nous Hermes 2 34B", 32768),
                        new ModelCardDto("starling-lm:7b", "Starling LM 7B", 32768),
                        new ModelCardDto("llava:13b", "LLaVA 13B", 4096),
                        new ModelCardDto("moondream:1.8b", "Moondream 1.8B", 4096),
                        new ModelCardDto("mxbai-embed-large:v1", "Mxbai Embed Large v1", 512)
                );
            case "anthropic":
                return List.of(
                        new ModelCardDto("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", 200000),
                        new ModelCardDto("claude-3-5-sonnet", "Claude 3.5 Sonnet Latest", 200000),
                        new ModelCardDto("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", 200000),
                        new ModelCardDto("claude-3-5-haiku", "Claude 3.5 Haiku Latest", 200000),
                        new ModelCardDto("claude-3-opus-20240229", "Claude 3 Opus", 200000),
                        new ModelCardDto("claude-3-opus", "Claude 3 Opus Latest", 200000),
                        new ModelCardDto("claude-3-sonnet-20240229", "Claude 3 Sonnet", 200000),
                        new ModelCardDto("claude-3-haiku-20240307", "Claude 3 Haiku", 200000)
                );
            case "gemini":
                return List.of(
                        new ModelCardDto("gemini-2.0-flash", "Gemini 2.0 Flash", 32768),
                        new ModelCardDto("gemini-2.0-flash-001", "Gemini 2.0 Flash 001", 32768),
                        new ModelCardDto("gemini-1.5-pro", "Gemini 1.5 Pro", 1000000),
                        new ModelCardDto("gemini-1.5-pro-002", "Gemini 1.5 Pro 002", 1000000),
                        new ModelCardDto("gemini-1.5-flash", "Gemini 1.5 Flash", 1000000),
                        new ModelCardDto("gemini-1.5-flash-001", "Gemini 1.5 Flash 001", 1000000),
                        new ModelCardDto("gemini-pro", "Gemini Pro", 32768),
                        new ModelCardDto("gemini-exp-1206", "Gemini Experimental 1206", 1000000),
                        new ModelCardDto("gemini-exp-1121", "Gemini Experimental 1121", 1000000),
                        new ModelCardDto("gemini-2.0-flash-thinking", "Gemini 2.0 Flash Thinking", 32768)
                );
            case "volcengine":
                return List.of(
                        new ModelCardDto("glm-5-2-260617", "GLM-5.2", 1000000),
                        new ModelCardDto("doubao-seed-2-1-pro-260628", "Doubao-Seed-2.1-pro", 128000),
                        new ModelCardDto("doubao-seed-2-1-lite-260628", "豆包-Seed 2.1 Lite", 128000),
                        new ModelCardDto("doubao-pro-128k", "豆包-Pro 128K", 128000),
                        new ModelCardDto("doubao-pro-32k", "豆包-Pro 32K", 32768),
                        new ModelCardDto("doubao-lite-32k", "豆包-Lite 32K", 32768),
                        new ModelCardDto("ernie-bot-45", "文心一言-4.5", 8192),
                        new ModelCardDto("ernie-bot-8k", "文心一言-8K", 8192),
                        new ModelCardDto("glm-4-air", "智谱AI-Glm4-Air", 8192),
                        new ModelCardDto("glm-4-plus", "智谱AI-Glm4-Plus", 32768),
                        new ModelCardDto("glm-4-9b-chat", "智谱AI-Glm4-9B-Chat", 8192),
                        new ModelCardDto("qwen-plus", "通义千问-plus", 128000),
                        new ModelCardDto("hunyuan-pro", "混元-pro", 32768),
                        new ModelCardDto("hunyuan-lite", "混元-lite", 32768),
                        new ModelCardDto("kimi", "Kimi", 128000),
                        new ModelCardDto("step-1-200k", "Step-1-200K", 200000),
                        new ModelCardDto("baichuan2-turbo", "百川智能-Turbo", 32768)
                );
            default:
                return List.of();
        }
    }

    /**
     * 模型列表响应 DTO：包含模型列表和提供商信息。
     */
    public static class ListModelResponse {
        private List<ModelCardDto> models;
        private String provider;

        public ListModelResponse() {}

        public ListModelResponse(List<ModelCardDto> models, String provider) {
            this.models = models;
            this.provider = provider;
        }

        public List<ModelCardDto> getModels() {
            return models;
        }

        public void setModels(List<ModelCardDto> models) {
            this.models = models;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }
    
    /**
     * 模型卡片 DTO：模型的基本信息。
     */
    public static class ModelCardDto {
        private String modelName;
        private String displayName;
        private Integer contextSize;

        public ModelCardDto() {}

        public ModelCardDto(String modelName, String displayName, Integer contextSize) {
            this.modelName = modelName;
            this.displayName = displayName;
            this.contextSize = contextSize;
        }

        // Getters and setters
        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public Integer getContextSize() {
            return contextSize;
        }

        public void setContextSize(Integer contextSize) {
            this.contextSize = contextSize;
        }
    }
}