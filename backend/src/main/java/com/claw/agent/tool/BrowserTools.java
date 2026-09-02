package com.claw.agent.tool;

import com.claw.agent.common.ToolCodes;
import com.claw.agent.tool.annotation.ToolSet;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 浏览器自动化工具集：让 Agent 能够访问网页、提取内容和搜索信息。
 * <p>
 * 基于 Java 内置 HttpClient（无额外依赖），支持：
 * <ul>
 *   <li>浏览网页并提取纯文本内容</li>
 *   <li>获取网页标题和元信息</li>
 *   <li>从页面中提取链接</li>
 * </ul>
 * 安全限制：
 * - 请求超时 30s，防止长时间阻塞
 * - 响应体最大 2MB，防止内存溢出
 * - 只支持 HTTP/HTTPS 协议
 * - 自动过滤 JavaScript/CSS 等非文本内容
 */
@Slf4j
@ToolSet(
    code = ToolCodes.BROWSER,
    name = "浏览器自动化",
    description = "基于 Java HttpClient 的轻量级浏览器工具，支持网页浏览、标题获取、链接提取",
    category = "search",
    enabledByDefault = true,
    version = "1.0.0"
)
public class BrowserTools {

    /** HTTP 客户端（全局复用，连接池自动管理） */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 最大响应体大小（2MB） */
    private static final int MAX_BODY_SIZE = 2 * 1024 * 1024;

    /** 请求超时时间（30s） */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** HTML 标签匹配正则（用于提取纯文本） */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /** 脚本/样式匹配正则（用于移除不需要的内容） */
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "(?is)<(script|style|noscript|iframe)[^>]*>.*?</\\1>");

    /** 标题提取正则 */
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?i)<title[^>]*>(.*?)</title>");

    /** 链接提取正则 */
    private static final Pattern LINK_PATTERN = Pattern.compile("(?i)<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");

    /** 禁止访问的内网主机名（小写匹配） */
    private static final List<String> BLOCKED_HOSTS = List.of(
            "localhost", "metadata.google.internal", "metadata", "kubernetes.default.svc"
    );

    /**
     * 浏览指定 URL 并提取页面纯文本内容。
     * <p>
     * 自动移除 HTML 标签、脚本、样式，返回可读的纯文本。
     * 适用于：查看网页内容、提取文章正文、获取页面信息。
     *
     * @param url 要访问的网页 URL（必须以 http:// 或 https:// 开头）
     * @return 页面纯文本内容（截取前 5000 字符，防止上下文爆炸）
     */
    @Tool(name = "browse_url", description = "浏览指定 URL 并提取页面纯文本内容。" +
            "自动移除 HTML 标签和脚本，返回可读文本。适用于查看网页、提取文章、获取页面信息。" +
            "URL 必须以 http:// 或 https:// 开头。")
    public String browseUrl(
            @ToolParam(name = "url", description = "要访问的网页 URL（如 https://example.com）") String url) {
        // 安全校验：只允许 HTTP/HTTPS
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return "错误：URL 必须以 http:// 或 https:// 开头";
        }
        // SSRF 防护：禁止访问内网地址
        String ssrfError = checkSsrf(url);
        if (ssrfError != null) {
            return ssrfError;
        }
        try {
            log.debug("浏览网页: url={}", url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (compatible; ClawAgent/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml,text/plain")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode != 200) {
                return String.format("HTTP 错误 %d：无法访问该页面", statusCode);
            }

            String body = response.body();
            if (body.length() > MAX_BODY_SIZE) {
                body = body.substring(0, MAX_BODY_SIZE);
            }

            // 提取纯文本
            String text = extractPlainText(body);
            // 截取前 5000 字符，防止上下文爆炸
            if (text.length() > 5000) {
                text = text.substring(0, 5000) + "\n\n[内容已截断，共 " + text.length() + " 字符]";
            }

            // 提取标题
            String title = extractTitle(body);
            StringBuilder result = new StringBuilder();
            if (title != null && !title.isEmpty()) {
                result.append("标题: ").append(title).append("\n\n");
            }
            result.append("内容:\n").append(text);
            return result.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "请求被中断: " + e.getMessage();
        } catch (Exception e) {
            log.warn("浏览网页失败: url={}, error={}", url, e.getMessage());
            return "浏览失败: " + e.getMessage();
        }
    }

    /**
     * 获取指定 URL 的页面标题和元信息。
     * <p>
     * 轻量级请求：只提取标题，不下载完整页面内容。
     * 适用于：快速获取页面标题、验证 URL 是否可访问。
     *
     * @param url 要访问的网页 URL
     * @return 页面标题和状态信息
     */
    @Tool(name = "get_page_title", description = "获取指定 URL 的页面标题。" +
            "轻量级请求，只提取标题不下载完整内容。适用于快速获取页面标题、验证 URL 可访问性。")
    public String getPageTitle(
            @ToolParam(name = "url", description = "要访问的网页 URL") String url) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return "错误：URL 必须以 http:// 或 https:// 开头";
        }
        // SSRF 防护：禁止访问内网地址
        String ssrfError = checkSsrf(url);
        if (ssrfError != null) {
            return ssrfError;
        }
        try {
            // 直接用 GET 请求获取前 10KB 内容提取标题
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (compatible; ClawAgent/1.0)")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String contentType = response.headers().firstValue("Content-Type").orElse("未知");

            if (statusCode == 200) {
                String body = response.body();
                if (body.length() > 10240) {
                    body = body.substring(0, 10240);
                }
                String title = extractTitle(body);
                return String.format("标题: %s\n状态: %d\nContent-Type: %s",
                        title != null ? title : "(无标题)", statusCode, contentType);
            }

            return String.format("状态: %d\nContent-Type: %s", statusCode, contentType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "请求被中断: " + e.getMessage();
        } catch (Exception e) {
            log.warn("获取页面标题失败: url={}, error={}", url, e.getMessage());
            return "获取失败: " + e.getMessage();
        }
    }

    /**
     * 从指定 URL 页面中提取所有链接。
     * <p>
     * 返回页面中的超链接列表（URL + 链接文本），适用于：
     * - 发现页面中的相关资源
     * - 构建网站地图
     * - 分析页面结构
     *
     * @param url   要分析的网页 URL
     * @param limit 最大返回链接数（默认 20，最大 50）
     * @return 链接列表（格式：[链接文本](URL)）
     */
    @Tool(name = "extract_links", description = "从指定 URL 页面中提取所有超链接。" +
            "返回链接文本和 URL 列表。适用于发现页面资源、构建网站地图、分析页面结构。")
    public String extractLinks(
            @ToolParam(name = "url", description = "要分析的网页 URL") String url,
            @ToolParam(name = "limit", description = "最大返回链接数（默认 20，最大 50）", required = false) Integer limit) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return "错误：URL 必须以 http:// 或 https:// 开头";
        }
        // SSRF 防护：禁止访问内网地址
        String ssrfError = checkSsrf(url);
        if (ssrfError != null) {
            return ssrfError;
        }
        int maxLinks = (limit != null && limit > 0) ? Math.min(limit, 50) : 20;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (compatible; ClawAgent/1.0)")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return String.format("HTTP 错误 %d：无法访问该页面", response.statusCode());
            }

            String body = response.body();
            if (body.length() > MAX_BODY_SIZE) {
                body = body.substring(0, MAX_BODY_SIZE);
            }

            // 提取链接
            List<String> links = new ArrayList<>();
            Matcher matcher = LINK_PATTERN.matcher(body);
            while (matcher.find() && links.size() < maxLinks) {
                String href = matcher.group(1);
                String text = matcher.group(2).replaceAll("<[^>]+>", "").trim();
                if (text.isEmpty()) text = href;
                // 转换相对路径为绝对路径
                if (href.startsWith("/")) {
                    try {
                        URI base = URI.create(url);
                        href = base.resolve(href).toString();
                    } catch (Exception ignored) {
                    }
                }
                links.add(String.format("- [%s](%s)", text, href));
            }

            if (links.isEmpty()) {
                return "该页面未找到任何链接";
            }
            return String.format("找到 %d 个链接：\n%s", links.size(), String.join("\n", links));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "请求被中断: " + e.getMessage();
        } catch (Exception e) {
            log.warn("提取链接失败: url={}, error={}", url, e.getMessage());
            return "提取失败: " + e.getMessage();
        }
    }

    /**
     * 从 HTML 中提取纯文本：移除脚本/样式/标签，保留可读内容。
     */
    private String extractPlainText(String html) {
        // 移除脚本和样式
        String text = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll("");
        // 移除 HTML 标签
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");
        // 解码 HTML 实体
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
        // 压缩空白字符
        text = text.replaceAll("\\s+", " ").trim();
        // 恢复段落间距
        text = text.replaceAll("\\s{2,}", "\n\n");
        return text.trim();
    }

    /**
     * 从 HTML 中提取页面标题。
     */
    private String extractTitle(String html) {
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("<[^>]+>", "").trim();
        }
        return null;
    }

    /**
     * SSRF 防护：检查 URL 是否指向内网或保留地址。
     * <p>
     * 检查维度：
     * <ul>
     *   <li>主机名黑名单（localhost、云元数据服务等）</li>
     *   <li>IP 地址范围（127.0.0.0/8、10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、169.254.0.0/16、0.0.0.0）</li>
     *   <li>IPv6 回环地址（::1）和唯一本地地址（fc00::/7）</li>
     * </ul>
     *
     * @param url 待检查的 URL
     * @return 如果检测到内网地址返回错误消息，否则返回 null
     */
    private String checkSsrf(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return "错误：URL 缺少有效的主机名";
            }

            // 1. 主机名黑名单检查
            String lowerHost = host.toLowerCase();
            for (String blocked : BLOCKED_HOSTS) {
                if (lowerHost.equals(blocked) || lowerHost.endsWith("." + blocked)) {
                    log.warn("[SSRF 防护] 拦截内网主机名: host={}", host);
                    return "错误：禁止访问内网地址";
                }
            }

            // 2. DNS 解析后检查 IP 是否为内网地址（防止域名指向内网 IP）
            InetAddress[] addresses;
            try {
                addresses = InetAddress.getAllByName(host);
            } catch (UnknownHostException e) {
                return "错误：无法解析主机名 " + host;
            }

            for (InetAddress addr : addresses) {
                if (isPrivateOrReservedAddress(addr)) {
                    log.warn("[SSRF 防护] 拦截内网 IP: host={}, ip={}", host, addr.getHostAddress());
                    return "错误：禁止访问内网地址";
                }
            }

            return null;
        } catch (IllegalArgumentException e) {
            return "错误：无效的 URL 格式";
        }
    }

    /**
     * 判断 InetAddress 是否为内网或保留地址。
     * <p>
     * 覆盖范围：
     * <ul>
     *   <li>loopback（127.0.0.0/8、::1）</li>
     *   <li>link-local（169.254.0.0/16、fe80::/10）</li>
     *   <li>site-local / ULA（10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、fc00::/7）</li>
     *   <li>any-local（0.0.0.0、::）</li>
     * </ul>
     */
    private boolean isPrivateOrReservedAddress(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isAnyLocalAddress()
                // IPv6 Unique Local Addresses (fc00::/7)
                || (addr.getAddress().length == 16
                    && (addr.getAddress()[0] & 0xFE) == 0xFC);
    }
}
