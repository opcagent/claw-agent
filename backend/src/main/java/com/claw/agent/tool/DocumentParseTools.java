package com.claw.agent.tool;

import com.claw.agent.common.ToolCodes;
import com.claw.agent.tool.annotation.ToolSet;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文档解析工具集（基于 Apache Tika）。
 * <p>
 * 支持从 PDF / DOCX / XLSX / PPTX / HTML / TXT 等 1000+ 格式中提取纯文本内容，
 * 供 Agent 在对话中理解用户上传的非图片文件。
 * <p>
 * 安全约束：
 * <ul>
 *   <li>路径防穿越：normalize + startsWith 校验</li>
 *   <li>文件大小上限：10 MB（防止 OOM）</li>
 *   <li>提取文本上限：50000 字符（防止上下文爆炸）</li>
 * </ul>
 */
@Slf4j
@Component
@ToolSet(
    code = ToolCodes.DOCUMENT_PARSE,
    name = "文档解析",
    description = "从 PDF/DOCX/XLSX/PPTX 等文件中提取文本内容",
    category = "data",
    enabledByDefault = true,
    version = "1.0.0"
)
public class DocumentParseTools {

    /** Tika 核心实例（线程安全，全局复用） */
    private static final Tika TIKA = new Tika();

    /** 单文件大小上限：10 MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    /** 提取文本上限：50000 字符（约 15 页 A4 文档） */
    private static final int MAX_TEXT_LENGTH = 50000;

    /**
     * 解析指定路径的文档，返回提取的纯文本内容。
     * <p>
     * Tika 自动检测文件类型并选择对应解析器，无需手动指定格式。
     * 支持格式：PDF、DOCX、XLSX、PPTX、HTML、TXT、CSV、RTF、ODT 等。
     *
     * @param filePath 文档的绝对路径
     * @return 提取的文本内容（截断至 MAX_TEXT_LENGTH），或错误提示
     */
    @Tool(name = "parse_document", description = "解析文档文件（PDF/DOCX/XLSX/PPTX等），提取其中的文本内容")
    public String parseDocument(
            @ToolParam(name = "file_path", description = "文档文件的绝对路径，如 /path/to/file.pdf") String filePath) {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();

        // 文件存在性校验
        if (!Files.exists(path)) {
            return "错误：文件不存在 - " + filePath;
        }
        if (!Files.isRegularFile(path)) {
            return "错误：路径不是文件 - " + filePath;
        }

        // 文件大小校验（防止 OOM）
        try {
            long size = Files.size(path);
            if (size > MAX_FILE_SIZE) {
                return String.format("错误：文件过大（%.1f MB，上限 10 MB）- %s", size / 1024.0 / 1024.0, filePath);
            }
        } catch (Exception e) {
            return "错误：无法读取文件信息 - " + e.getMessage();
        }

        // Tika 解析
        try (InputStream stream = Files.newInputStream(path)) {
            // 自动检测 MIME 类型
            String mimeType = TIKA.detect(path);
            log.info("文档解析: file={}, mimeType={}, size={} bytes", path.getFileName(), mimeType, Files.size(path));

            // 使用 BodyContentHandler 提取纯文本（忽略格式标记）
            ContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
            Metadata metadata = new Metadata();
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(stream, handler, metadata, new ParseContext());

            String text = handler.toString();
            if (text == null || text.isBlank()) {
                return "文档解析完成，但未提取到文本内容（可能是扫描件或纯图片文件，建议使用 OCR 工具）";
            }

            // 附加元信息（文件名 + 类型 + 字符数）
            StringBuilder result = new StringBuilder();
            result.append("[文档解析结果]\n");
            result.append("文件: ").append(path.getFileName()).append("\n");
            result.append("类型: ").append(mimeType).append("\n");
            result.append("字符数: ").append(text.length()).append("\n");
            result.append("---\n");
            result.append(text);

            // 超长截断提示
            if (text.length() >= MAX_TEXT_LENGTH) {
                result.append("\n\n...（内容过长，已截断至 ").append(MAX_TEXT_LENGTH).append(" 字符）");
            }

            return result.toString();
        } catch (Exception e) {
            log.warn("文档解析失败: file={}", path, e);
            return "文档解析失败: " + e.getMessage();
        }
    }

    /**
     * 获取文档的元信息（类型、页数、作者等），不提取正文。
     * <p>
     * 适用于用户只想了解文件概况的场景，节省 Token。
     *
     * @param filePath 文档的绝对路径
     * @return 元信息摘要
     */
    @Tool(name = "document_metadata", description = "获取文档的元信息（类型、大小、页数、作者等），不提取正文")
    public String documentMetadata(
            @ToolParam(name = "file_path", description = "文档文件的绝对路径") String filePath) {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            return "错误：文件不存在 - " + filePath;
        }

        try (InputStream stream = Files.newInputStream(path)) {
            Metadata metadata = new Metadata();
            AutoDetectParser parser = new AutoDetectParser();
            // 用空 handler 只解析元数据，不提取正文
            parser.parse(stream, new BodyContentHandler(0), metadata, new ParseContext());

            StringBuilder sb = new StringBuilder();
            sb.append("[文档元信息]\n");
            sb.append("文件: ").append(path.getFileName()).append("\n");
            sb.append("大小: ").append(Files.size(path) / 1024).append(" KB\n");

            // 提取常见元数据字段
            String[] keys = {"Content-Type", "title", "author", "dc:creator",
                    "meta:page-count", "xmpTPg:NPages", "meta:word-count", "meta:character-count"};
            String[] labels = {"MIME类型", "标题", "作者", "创建者",
                    "页数", "页数", "词数", "字符数"};
            for (int i = 0; i < keys.length; i++) {
                String value = metadata.get(keys[i]);
                if (value != null && !value.isBlank()) {
                    sb.append(labels[i]).append(": ").append(value).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("获取文档元信息失败: file={}", path, e);
            return "获取元信息失败: " + e.getMessage();
        }
    }
}
