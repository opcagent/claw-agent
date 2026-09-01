package com.claw.agent.config.tool;

import com.claw.agent.common.ToolCodes;
import com.claw.agent.config.infra.HttpProxyConfig;
import com.claw.agent.service.ConfigService;
import com.claw.agent.tool.MultiSearchTools;
import com.claw.agent.tool.NoteTools;
import com.claw.agent.tool.OcrTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 内置工具工厂实现。
 * <p>
 * 为需要特殊参数的内置工具提供工厂方法，支持依赖注入。
 * 当前支持: MultiSearchTools(多引擎+代理)、OcrTools(OCR识别-多厂商降级)。
 */
@Component
@RequiredArgsConstructor
public class BuiltinToolFactory implements ToolFactory<Object> {

    private final HttpProxyConfig proxyConfig;
    private final SearchEngineConfig searchEngineConfig;
    private final OcrConfig ocrConfig;
    private final ConfigService configService;
    private final OcrTools ocrTools;

    @Override
    public Object create(com.claw.agent.config.tool.ToolRegistry.ToolMetadata metadata) {
        String code = metadata.getCode();
        
        // NoteTools 需要 workspace 参数，由调用方单独处理
        if (ToolCodes.NOTE_TOOLS.equals(code)) {
            throw new IllegalArgumentException(
                "NoteTools 需要 workspace 参数，请使用 AgentRegistry 中的特殊处理逻辑");
        }
        
        // MultiSearchTools 需要 searchEngineConfig + configService + proxyConfig
        if (ToolCodes.MULTI_SEARCH.equals(code)) {
            return new MultiSearchTools(searchEngineConfig, configService, proxyConfig);
        }

        // OcrTools 直接返回 Spring 管理的单例 Bean（共享百度 Token 缓存）
        if (ToolCodes.OCR.equals(code)) {
            return ocrTools;
        }
        
        throw new IllegalArgumentException("不支持的工具集: " + code);
    }

    @Override
    public boolean supports(String code) {
        return ToolCodes.MULTI_SEARCH.equals(code) || ToolCodes.OCR.equals(code);
    }
}
