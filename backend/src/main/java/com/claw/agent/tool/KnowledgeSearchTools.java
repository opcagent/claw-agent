package com.claw.agent.tool;

import com.claw.agent.common.ToolCodes;
import com.claw.agent.tool.annotation.ToolSet;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 知识库检索工具集：基于关键词的工作区知识文件搜索。
 * <p>
 * 扫描 {@code workspace/knowledge/} 目录下的 Markdown / 文本文件，
 * 按关键词匹配度排序返回相关片段。作为轻量 RAG 方案，
 * 无需向量数据库依赖，适合个人知识量级（数十到数百篇文档）。
 * <p>
 * 匹配策略：对每个文件按行扫描，命中关键词的行及其上下文行组成片段，
 * 按命中行数排序（越多越相关），返回 Top-N 结果。
 */
@Slf4j
@ToolSet(
    code = ToolCodes.KNOWLEDGE_SEARCH,
    name = "知识库检索",
    description = "搜索用户工作区知识库中的文档，按关键词匹配返回相关片段",
    category = "search",
    enabledByDefault = true,
    version = "1.0.0"
)
public class KnowledgeSearchTools {

    /** 知识库根目录（工作区下的 knowledge/ 子目录） */
    private final Path knowledgeRoot;

    /** 支持的文件扩展名 */
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            ".md", ".txt", ".text", ".rst", ".log", ".csv", ".json", ".yaml", ".yml"
    );

    /** 搜索结果上下文行数（命中行前后各取 N 行） */
    private static final int CONTEXT_LINES = 2;

    /** 单个片段最大字符数（避免返回过长内容撑爆上下文） */
    private static final int MAX_SNIPPET_LENGTH = 500;

    /** 最大返回结果数 */
    private static final int MAX_RESULTS = 5;

    public KnowledgeSearchTools(String workspaceDir) {
        this.knowledgeRoot = Paths.get(workspaceDir, "knowledge").toAbsolutePath().normalize();
        try {
            Files.createDirectories(knowledgeRoot);
        } catch (IOException e) {
            log.warn("创建知识库目录失败: {}", knowledgeRoot, e);
        }
    }

    /**
     * 搜索知识库：按关键词在知识库文件中匹配，返回最相关的文档片段。
     *
     * @param query 搜索关键词（支持多个词，空格分隔，任一词命中即算匹配）
     * @return 按相关度排序的搜索结果（文件名 + 匹配片段）
     */
    @Tool(name = "search_knowledge", description = "在知识库中搜索与查询关键词相关的文档片段，返回最匹配的内容")
    public String searchKnowledge(
            @ToolParam(name = "query", description = "搜索关键词，多个词用空格分隔") String query) {
        if (query == null || query.isBlank()) {
            return "搜索关键词不能为空";
        }
        if (!Files.exists(knowledgeRoot)) {
            return "知识库目录不存在，请先添加知识文档";
        }

        // 分词：按空格拆分，全部转小写
        String[] keywords = query.toLowerCase(Locale.ROOT).split("\\s+");

        List<SearchResult> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(knowledgeRoot)) {
            stream.filter(Files::isRegularFile)
                  .filter(this::isSupportedFile)
                  .forEach(path -> {
                      SearchResult result = searchInFile(path, keywords);
                      if (result != null) {
                          results.add(result);
                      }
                  });
        } catch (IOException e) {
            log.warn("扫描知识库目录失败: {}", knowledgeRoot, e);
            return "知识库扫描失败: " + e.getMessage();
        }

        if (results.isEmpty()) {
            return "未找到与 \"" + query + "\" 相关的知识文档";
        }

        // 按命中行数降序排列，取 Top-N
        results.sort(Comparator.comparingInt(SearchResult::hitCount).reversed());
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(results.size(), MAX_RESULTS);
        for (int i = 0; i < limit; i++) {
            SearchResult r = results.get(i);
            sb.append("### ").append(i + 1).append(". ").append(r.relativePath)
              .append("（命中 ").append(r.hitCount).append(" 处）\n\n");
            sb.append(r.snippet).append("\n\n---\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * 列出知识库中的所有文档。
     *
     * @return 文档列表（文件名 + 大小）
     */
    @Tool(name = "list_knowledge", description = "列出知识库中的所有文档文件")
    public String listKnowledge() {
        if (!Files.exists(knowledgeRoot)) {
            return "知识库目录不存在";
        }
        List<String> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(knowledgeRoot)) {
            stream.filter(Files::isRegularFile)
                  .filter(this::isSupportedFile)
                  .sorted()
                  .forEach(path -> {
                      try {
                          String rel = knowledgeRoot.relativize(path).toString();
                          long size = Files.size(path);
                          entries.add(String.format("- %s（%s）", rel, formatSize(size)));
                      } catch (IOException e) {
                          log.warn("获取文件大小失败: {}", path, e);
                      }
                  });
        } catch (IOException e) {
            log.warn("扫描知识库目录失败: {}", knowledgeRoot, e);
            return "知识库扫描失败: " + e.getMessage();
        }
        if (entries.isEmpty()) {
            return "（知识库为空，请将文档放入 knowledge/ 目录）";
        }
        return String.join("\n", entries);
    }

    /**
     * 读取知识库中指定文档的完整内容。
     *
     * @param path 文档相对路径（相对于 knowledge/ 目录）
     * @return 文档全文内容
     */
    @Tool(name = "read_knowledge", description = "读取知识库中指定文档的完整内容")
    public String readKnowledge(
            @ToolParam(name = "path", description = "文档路径，如 guides/setup.md") String path) {
        Path file = safePath(path);
        if (!Files.exists(file)) {
            return "文档不存在: " + path;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.length() > 5000) {
                return content.substring(0, 5000) + "\n\n...（内容过长，已截断，共 " + content.length() + " 字符）";
            }
            return content;
        } catch (IOException e) {
            log.warn("读取知识文档失败: {}", file, e);
            return "读取失败: " + e.getMessage();
        }
    }

    /** 在单个文件中搜索关键词，返回匹配结果 */
    private SearchResult searchInFile(Path file, String[] keywords) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<Integer> hitLineIndexes = new ArrayList<>();

            for (int i = 0; i < lines.size(); i++) {
                String lower = lines.get(i).toLowerCase(Locale.ROOT);
                for (String kw : keywords) {
                    if (lower.contains(kw)) {
                        hitLineIndexes.add(i);
                        break;
                    }
                }
            }

            if (hitLineIndexes.isEmpty()) {
                return null;
            }

            // 构建片段：取命中行及上下文
            String snippet = buildSnippet(lines, hitLineIndexes);
            String relativePath = knowledgeRoot.relativize(file).toString();
            return new SearchResult(relativePath, snippet, hitLineIndexes.size());
        } catch (Exception e) {
            log.debug("搜索文件失败（跳过）: {}", file, e);
            return null;
        }
    }

    /** 根据命中行索引构建上下文片段 */
    private String buildSnippet(List<String> lines, List<Integer> hitIndexes) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, hitIndexes.get(0) - CONTEXT_LINES);
        int end = Math.min(lines.size() - 1, hitIndexes.get(hitIndexes.size() - 1) + CONTEXT_LINES);

        for (int i = start; i <= end; i++) {
            sb.append(lines.get(i));
            if (i < end) sb.append("\n");
            if (sb.length() > MAX_SNIPPET_LENGTH) {
                sb.setLength(MAX_SNIPPET_LENGTH);
                sb.append("...");
                break;
            }
        }
        return sb.toString();
    }

    /** 路径安全校验：防止目录穿越 */
    private Path safePath(String relativePath) {
        Path file = knowledgeRoot.resolve(relativePath).toAbsolutePath().normalize();
        if (!file.startsWith(knowledgeRoot)) {
            throw new IllegalArgumentException("非法知识库路径: " + relativePath);
        }
        return file;
    }

    /** 判断文件是否为支持的格式 */
    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /** 文件大小格式化 */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    /** 搜索结果记录 */
    private record SearchResult(String relativePath, String snippet, int hitCount) {}
}
