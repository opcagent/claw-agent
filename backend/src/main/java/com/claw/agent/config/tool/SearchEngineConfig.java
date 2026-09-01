package com.claw.agent.config.tool;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多搜索引擎配置。
 * <p>
 * 支持 Tavily / Brave / Bing / SearXNG / DuckDuckGo 多级降级链：
 * <ol>
 *   <li>Tavily — AI Agent 专属，返回 LLM 友好结构化结果（1000 次/月免费）</li>
 *   <li>Brave — 独立索引，不依赖 Google（~1000 次/月免费额度）</li>
 *   <li>Bing — 微软必应搜索（需 API Key，中国大陆可直连）</li>
 *   <li>SearXNG — 自托管元搜索引擎，聚合 70+ 引擎（完全免费无限制）</li>
 *   <li>DuckDuckGo — HTML 端点爬，零配置兜底（需代理）</li>
 * </ol>
 * 搜索时按优先级依次尝试，任一引擎成功即返回；全部失败才返回错误。
 */
@Data
@Component
@ConfigurationProperties(prefix = "claw.search")
public class SearchEngineConfig {

    /** 搜索引擎优先级（代码按此顺序降级） */
    private String[] engines = {"tavily", "brave", "bing", "searxng", "duckduckgo"};

    /** 全局搜索超时（秒） */
    private int timeoutSeconds = 15;

    /** 全局最大返回条数 */
    private int maxResults = 8;

    /** Tavily 配置 */
    private Tavily tavily = new Tavily();

    /** Brave 配置 */
    private Brave brave = new Brave();

    /** Bing 配置 */
    private Bing bing = new Bing();

    /** SearXNG 配置 */
    private Searxng searxng = new Searxng();

    @Data
    public static class Tavily {
        /** API Key（https://app.tavily.com 注册获取，免费 1000 次/月） */
        private String apiKey;
        /** API 端点 */
        private String baseUrl = "https://api.tavily.com";
        /** 搜索深度：basic / advanced */
        private String searchDepth = "basic";

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isEmpty();
        }
    }

    @Data
    public static class Brave {
        /** API Key（https://brave.com/search/api 注册获取，~1000 次/月免费） */
        private String apiKey;
        /** API 端点 */
        private String baseUrl = "https://api.search.brave.com";

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isEmpty();
        }
    }

    @Data
    public static class Bing {
        /** API Key（Azure 门户创建 "Bing Search v7" 资源获取） */
        private String apiKey;
        /** API 端点 */
        private String baseUrl = "https://api.bing.microsoft.com";

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isEmpty();
        }
    }

    @Data
    public static class Searxng {
        /** SearXNG 实例地址（本地 Docker 或公共实例） */
        private String baseUrl;
        /** 使用的后端引擎（如 google,bing） */
        private String engines = "google,bing";

        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isEmpty();
        }
    }
}
