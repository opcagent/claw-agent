package com.claw.agent.common;

/**
 * 工具集 code 常量。
 * <p>
 * 收敛所有工具集的唯一标识符，避免各模块重复定义字符串魔法值。
 * 新增工具集时必须在此登记，并在 {@code BuiltinToolFactory} / {@code AgentRegistry} 中完成注册。
 */
public final class ToolCodes {

    private ToolCodes() {}

    // ---- 内置工具（AgentScope 框架提供，无需特殊构造参数） ----

    /** 文件系统读写 */
    public static final String FILESYSTEM = "filesystem";
    /** Shell 命令执行 */
    public static final String SHELL = "shell";
    /** 长期记忆 */
    public static final String MEMORY = "memory";
    /** 数学计算 */
    public static final String MATH_TOOLS = "math_tools";
    /** 系统工具（时间、UUID 等） */
    public static final String SYSTEM_TOOLS = "system_tools";

    // ---- 自定义工具（项目自研，部分需要特殊构造参数） ----

    /** 知识库笔记（需要 workspace 参数，AgentRegistry 手动注册） */
    public static final String NOTE_TOOLS = "note_tools";
    /** 联网搜索（多引擎降级：Tavily → Brave → Bing → SearXNG → DuckDuckGo） */
    public static final String MULTI_SEARCH = "multi_search";
    /** 邮件发送（需要 EmailService 依赖，AgentRegistry 手动注册） */
    public static final String EMAIL_TOOLS = "email_tools";
    /** 知识库检索（需要 workspace 参数，AgentRegistry 手动注册） */
    public static final String KNOWLEDGE_SEARCH = "knowledge_search";
    /** OCR 图片文字识别（多厂商降级：百度智能云 → 腾讯云，BuiltinToolFactory 工厂注册） */
    public static final String OCR = "ocr";
    /** 文档解析（Apache Tika：PDF/DOCX/XLSX/PPTX 等格式文本提取，Spring Bean 注册） */
    public static final String DOCUMENT_PARSE = "document_parse";
}
