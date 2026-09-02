package com.claw.agent.tool;

import com.claw.agent.config.infra.HttpProxyConfig;
import com.claw.agent.common.ToolCodes;
import com.claw.agent.config.tool.SearchEngineConfig;
import com.claw.agent.service.ConfigService;
import com.claw.agent.tool.annotation.ToolSet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.HtmlUtils;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多引擎联网搜索工具（六级自动降级）。
 * <p>
 * 降级链：Tavily → Brave → Bing → SearXNG → DuckDuckGo。
 * 搜索时按优先级依次尝试，任一引擎成功即返回；全部失败才返回错误提示。
 * <ul>
 *   <li>Tavily — AI Agent 专属，返回 LLM 友好结构化结果（1000 次/月免费）</li>
 *   <li>Brave — 独立索引，不依赖 Google（~1000 次/月免费额度）</li>
 *   <li>Bing — 微软必应搜索（需 API Key，中国大陆可直连）</li>
 *   <li>SearXNG — 自托管元搜索引擎，聚合 70+ 引擎（完全免费无限制）</li>
 *   <li>DuckDuckGo — HTML 端点爬，零配置兜底（需代理）</li>
 * </ul>
 */
@Slf4j
@ToolSet(
    code = ToolCodes.MULTI_SEARCH,
    name = "多引擎联网搜索",
    description = "支持 Tavily/Brave/Bing/SearXNG/DuckDuckGo 多级降级的联网搜索，自动选择最优引擎",
    category = "search",
    enabledByDefault = true,
    version = "2.0.0"
)
public class MultiSearchTools {

    /** DuckDuckGo HTML 结果解析正则 */
    private static final Pattern DDG_RESULT_PATTERN = Pattern.compile(
            "<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern DDG_SNIPPET_PATTERN = Pattern.compile(
            "<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL);

    private final SearchEngineConfig searchConfig;
    private final ConfigService configService;
    private final HttpProxyConfig proxyConfig;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MultiSearchTools(SearchEngineConfig searchConfig, ConfigService configService,
                            HttpProxyConfig proxyConfig) {
        this.searchConfig = searchConfig;
        this.configService = configService;
        this.proxyConfig = proxyConfig;
        this.objectMapper = new ObjectMapper();

        var clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);

        if (proxyConfig.isConfigured()) {
            String host = proxyConfig.getHttp().getHost();
            int port = proxyConfig.getHttp().getPort();
            clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(host, port)));
            log.info("MultiSearchTools: 已启用代理 {}:{}", host, port);
        }

        this.httpClient = clientBuilder.build();
    }

    /**
     * 动态解析搜索引擎 API Key（数据库优先，回退 application.yml）。
     * <p>
     * 数据库按 USER > TENANT > PLATFORM 三级作用域解析，
     * 未配置时回退到 SearchEngineConfig（application.yml 默认值）。
     *
     * @param configKey  配置键
     * @param ymlApiKey  application.yml 中的默认值（回退）
     * @param rc         框架透传的运行时上下文（包含用户信息）
     */
    private String resolveApiKey(String configKey, String ymlApiKey, RuntimeContext rc) {
        // 1. 尝试从数据库解析（从 RuntimeContext 获取用户信息，而非 ThreadLocal）
        String userId = rc != null ? (String) rc.get("userId") : null;
        if (userId != null) {
            Long tenantId = rc.get("tenantId");
            String dbKey = configService.resolveSearchApiKey(configKey, tenantId, userId);
            if (dbKey != null && !dbKey.isEmpty()) {
                return dbKey;
            }
        }
        // 2. 回退到 application.yml 默认值
        return ymlApiKey;
    }

    /** 解析 SearXNG 实例地址（数据库优先，回退 application.yml） */
    private String resolveSearxngBaseUrl(RuntimeContext rc) {
        String userId = rc != null ? (String) rc.get("userId") : null;
        if (userId != null) {
            Long tenantId = rc.get("tenantId");
            String dbUrl = configService.resolveSearchApiKey(
                    ConfigService.KEY_SEARCH_SEARXNG_BASE_URL, tenantId, userId);
            if (dbUrl != null && !dbUrl.isEmpty()) {
                return dbUrl;
            }
        }
        return searchConfig.getSearxng().getBaseUrl();
    }

    @Tool(name = "web_search", description = "联网搜索：输入关键词，自动选择最优搜索引擎（Tavily/Brave/Bing/SearXNG/DuckDuckGo），返回网页标题、链接与摘要")
    public String webSearch(
            @ToolParam(name = "query", description = "搜索关键词") String query,
            RuntimeContext rc) {
        List<String> errors = new ArrayList<>();

        for (String engine : searchConfig.getEngines()) {
            try {
                String result = switch (engine) {
                    case "tavily" -> searchTavily(query, rc);
                    case "brave" -> searchBrave(query, rc);
                    case "bing" -> searchBing(query, rc);
                    case "searxng" -> searchSearxng(query, rc);
                    case "duckduckgo" -> searchDuckDuckGo(query);
                    default -> null;
                };
                if (result != null && !result.isEmpty()) {
                    log.info("搜索引擎 {} 成功: query={}", engine, query);
                    return result;
                }
            } catch (InterruptedException e) {
                // ExecutionConfig 超时 interrupt 工作线程 → HTTP 请求被中断，属预期行为
                Thread.currentThread().interrupt(); // 恢复中断标志
                String msg = engine + ": 请求超时被中断";
                errors.add(msg);
                log.warn("搜索引擎 {} 超时中断: query={}", engine, query);
            } catch (Exception e) {
                String msg = engine + ": " + e.getMessage();
                errors.add(msg);
                log.warn("搜索引擎 {} 失败: query={}", engine, query, e);
            }
        }

        return "所有搜索引擎均失败，请检查网络或稍后重试。\n失败详情：\n" + String.join("\n", errors);
    }

    @Tool(name = "search_engines_status", description = "查看各搜索引擎的配置状态和可用性")
    public String getEnginesStatus(RuntimeContext rc) {
        // 从 RuntimeContext 获取用户信息（框架透传，reactor 线程可用）
        String userId = rc != null ? (String) rc.get("userId") : null;
        Long tenantId = rc != null ? (Long) rc.get("tenantId") : null;

        String tavilyKey = configService.resolveSearchApiKey(
                ConfigService.KEY_SEARCH_TAVILY_API_KEY, tenantId, userId);
        String braveKey = configService.resolveSearchApiKey(
                ConfigService.KEY_SEARCH_BRAVE_API_KEY, tenantId, userId);
        String bingKey = configService.resolveSearchApiKey(
                ConfigService.KEY_SEARCH_BING_API_KEY, tenantId, userId);
        String searxngUrl = configService.resolveSearchApiKey(
                ConfigService.KEY_SEARCH_SEARXNG_BASE_URL, tenantId, userId);

        // 数据库未配置时回退到 application.yml
        if (tavilyKey == null || tavilyKey.isEmpty()) tavilyKey = searchConfig.getTavily().getApiKey();
        if (braveKey == null || braveKey.isEmpty()) braveKey = searchConfig.getBrave().getApiKey();
        if (bingKey == null || bingKey.isEmpty()) bingKey = searchConfig.getBing().getApiKey();
        if (searxngUrl == null || searxngUrl.isEmpty()) searxngUrl = searchConfig.getSearxng().getBaseUrl();

        StringBuilder sb = new StringBuilder("搜索引擎状态：\n");
        sb.append("1. Tavily: ").append(tavilyKey != null && !tavilyKey.isEmpty() ? "✅ 已配置" : "❌ 未配置 API Key").append("\n");
        sb.append("2. Brave: ").append(braveKey != null && !braveKey.isEmpty() ? "✅ 已配置" : "❌ 未配置 API Key").append("\n");
        sb.append("3. Bing: ").append(bingKey != null && !bingKey.isEmpty() ? "✅ 已配置" : "❌ 未配置 API Key").append("\n");
        sb.append("4. SearXNG: ").append(searxngUrl != null && !searxngUrl.isEmpty() ? "✅ 已配置 (" + searxngUrl + ")" : "❌ 未配置实例地址").append("\n");
        sb.append("5. DuckDuckGo: ✅ 始终可用（兜底）\n");
        sb.append("\n降级顺序: ").append(String.join(" → ", searchConfig.getEngines()));
        return sb.toString();
    }

    // ==================== Tavily ====================

    private String searchTavily(String query, RuntimeContext rc) throws Exception {
        String apiKey = resolveApiKey(ConfigService.KEY_SEARCH_TAVILY_API_KEY,
                searchConfig.getTavily().getApiKey(), rc);
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }

        String url = searchConfig.getTavily().getBaseUrl() + "/search";
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "api_key", apiKey,
                "query", query,
                "max_results", searchConfig.getMaxResults(),
                "search_depth", searchConfig.getTavily().getSearchDepth()
        ));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(searchConfig.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Tavily 返回状态码: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("搜索结果（Tavily · ").append(query).append("）：\n");
        int count = 0;
        for (JsonNode r : results) {
            if (count >= searchConfig.getMaxResults()) break;
            sb.append("\n").append(++count).append(". ").append(r.path("title").asText("(无标题)"))
                    .append("\n   链接: ").append(r.path("url").asText(""))
                    .append("\n   摘要: ").append(r.path("content").asText(""));
        }
        return count > 0 ? sb.toString() : null;
    }

    // ==================== Brave ====================

    private String searchBrave(String query, RuntimeContext rc) throws Exception {
        String apiKey = resolveApiKey(ConfigService.KEY_SEARCH_BRAVE_API_KEY,
                searchConfig.getBrave().getApiKey(), rc);
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }

        String url = searchConfig.getBrave().getBaseUrl() + "/res/v1/web/search?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&count=" + searchConfig.getMaxResults();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("X-Subscription-Token", apiKey)
                .timeout(Duration.ofSeconds(searchConfig.getTimeoutSeconds()))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Brave 返回状态码: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode results = root.path("web").path("results");
        if (!results.isArray()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("搜索结果（Brave · ").append(query).append("）：\n");
        int count = 0;
        for (JsonNode r : results) {
            if (count >= searchConfig.getMaxResults()) break;
            sb.append("\n").append(++count).append(". ").append(r.path("title").asText("(无标题)"))
                    .append("\n   链接: ").append(r.path("url").asText(""))
                    .append("\n   摘要: ").append(r.path("description").asText(""));
        }
        return count > 0 ? sb.toString() : null;
    }

    // ==================== Bing ====================

    private String searchBing(String query, RuntimeContext rc) throws Exception {
        String apiKey = resolveApiKey(ConfigService.KEY_SEARCH_BING_API_KEY,
                searchConfig.getBing().getApiKey(), rc);
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }

        String url = searchConfig.getBing().getBaseUrl() + "/v7.0/search?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&count=" + searchConfig.getMaxResults() + "&mkt=zh-CN";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("User-Agent", "Mozilla/5.0 (compatible; ClawAgent/1.0)")
                .timeout(Duration.ofSeconds(searchConfig.getTimeoutSeconds()))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Bing 返回状态码: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode results = root.path("webPages").path("value");
        if (!results.isArray()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("搜索结果（Bing · ").append(query).append("）：\n");
        int count = 0;
        for (JsonNode r : results) {
            if (count >= searchConfig.getMaxResults()) break;
            sb.append("\n").append(++count).append(". ").append(r.path("name").asText("(无标题)"))
                    .append("\n   链接: ").append(r.path("url").asText(""))
                    .append("\n   摘要: ").append(r.path("snippet").asText(""));
        }
        return count > 0 ? sb.toString() : null;
    }

    // ==================== SearXNG ====================

    private String searchSearxng(String query, RuntimeContext rc) throws Exception {
        String baseUrl = resolveSearxngBaseUrl(rc);
        if (baseUrl == null || baseUrl.isEmpty()) {
            return null;
        }

        String url = baseUrl + "/search?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&format=json"
                + "&engines=" + searchConfig.getSearxng().getEngines()
                + "&limit=" + searchConfig.getMaxResults();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(searchConfig.getTimeoutSeconds()))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("SearXNG 返回状态码: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("搜索结果（SearXNG · ").append(query).append("）：\n");
        int count = 0;
        for (JsonNode r : results) {
            if (count >= searchConfig.getMaxResults()) break;
            sb.append("\n").append(++count).append(". ").append(r.path("title").asText("(无标题)"))
                    .append("\n   链接: ").append(r.path("url").asText(""))
                    .append("\n   摘要: ").append(r.path("content").asText(""));
        }
        return count > 0 ? sb.toString() : null;
    }

    // ==================== DuckDuckGo ====================

    private String searchDuckDuckGo(String query) throws Exception {
        String url = "https://html.duckduckgo.com/html/?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; ClawAgent/1.0)")
                .timeout(Duration.ofSeconds(searchConfig.getTimeoutSeconds()))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseDuckDuckGoResults(query, response.body());
    }

    private String parseDuckDuckGoResults(String query, String html) {
        List<String[]> links = new ArrayList<>();
        List<String> snippets = new ArrayList<>();

        Matcher linkMatcher = DDG_RESULT_PATTERN.matcher(html);
        while (linkMatcher.find() && links.size() < searchConfig.getMaxResults()) {
            links.add(new String[]{linkMatcher.group(1), cleanHtml(linkMatcher.group(2))});
        }
        Matcher snippetMatcher = DDG_SNIPPET_PATTERN.matcher(html);
        while (snippetMatcher.find() && snippets.size() < searchConfig.getMaxResults()) {
            snippets.add(cleanHtml(snippetMatcher.group(1)));
        }

        if (links.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("搜索结果（DuckDuckGo · ").append(query).append("）：\n");
        for (int i = 0; i < links.size(); i++) {
            sb.append("\n").append(i + 1).append(". ").append(links.get(i)[1])
                    .append("\n   链接: ").append(links.get(i)[0]);
            if (i < snippets.size()) {
                sb.append("\n   摘要: ").append(snippets.get(i));
            }
        }
        return sb.toString();
    }

    private String cleanHtml(String fragment) {
        String noTag = fragment.replaceAll("<[^>]+>", "");
        return HtmlUtils.htmlUnescape(noTag).replaceAll("\\s+", " ").trim();
    }
}
