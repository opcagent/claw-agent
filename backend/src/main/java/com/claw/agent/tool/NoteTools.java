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
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 知识库笔记工具集。
 * <p>
 * 提供用户工作区笔记的增删改查功能。
 */
@Slf4j
@ToolSet(
    code = ToolCodes.NOTE_TOOLS,
    name = "知识库笔记",
    description = "用户工作区笔记的增删改查",
    category = "data",
    enabledByDefault = true,
    version = "1.0.0"
)
public class NoteTools {

    /** 笔记根目录（工作区下的 notes/ 子目录） */
    private final Path notesRoot;

    /** 时间戳格式：用于默认笔记标题 */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public NoteTools(String workspaceDir) {
        this.notesRoot = Paths.get(workspaceDir, "notes").toAbsolutePath().normalize();
        try {
            Files.createDirectories(notesRoot);
        } catch (IOException e) {
            log.warn("创建笔记目录失败: {}", notesRoot, e);
        }
    }

    @Tool(name = "list_notes", description = "列出知识库中的所有笔记文件名")
    public String listNotes() throws IOException {
        try (Stream<Path> stream = Files.list(notesRoot)) {
            List<String> names = stream
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
            return names.isEmpty() ? "（暂无笔记）" : String.join("\n", names);
        }
    }

    @Tool(name = "read_note", description = "读取指定笔记的完整内容")
    public String readNote(
            @ToolParam(name = "name", description = "笔记文件名，如 todo.md") String name) throws IOException {
        Path file = safePath(name);
        if (!Files.exists(file)) {
            return "笔记不存在: " + name;
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Tool(name = "save_note", description = "保存（创建或覆盖）一条笔记，内容为 Markdown 格式")
    public String saveNote(
            @ToolParam(name = "name", description = "笔记文件名，必须以 .md 结尾") String name,
            @ToolParam(name = "content", description = "笔记内容（Markdown）") String content) throws IOException {
        Path file = safePath(name);
        String body = "> 更新于 " + LocalDateTime.now().format(TIMESTAMP) + "\n\n" + content;
        Files.writeString(file, body, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return "已保存笔记: " + file.getFileName();
    }

    @Tool(name = "append_note", description = "在指定笔记末尾追加内容；笔记不存在时自动创建")
    public String appendNote(
            @ToolParam(name = "name", description = "笔记文件名，必须以 .md 结尾") String name,
            @ToolParam(name = "content", description = "要追加的内容（Markdown）") String content) throws IOException {
        Path file = safePath(name);
        String body = "\n## " + LocalDateTime.now().format(TIMESTAMP) + "\n" + content + "\n";
        Files.writeString(file, body, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return "已追加到笔记: " + file.getFileName();
    }

    @Tool(name = "delete_note", description = "删除指定笔记（是否需确认取决于 permission_mode 配置）")
    public String deleteNote(
            @ToolParam(name = "name", description = "笔记文件名") String name) throws IOException {
        Path file = safePath(name);
        if (!Files.exists(file)) {
            return "笔记不存在: " + name;
        }
        Files.delete(file);
        return "已删除笔记: " + name;
    }

    /** 路径归一化 + 越权校验：防止 ../ 逃逸出笔记目录 */
    private Path safePath(String name) {
        Path file = notesRoot.resolve(name).toAbsolutePath().normalize();
        if (!file.startsWith(notesRoot)) {
            throw new IllegalArgumentException("非法笔记路径: " + name);
        }
        return file;
    }
}
