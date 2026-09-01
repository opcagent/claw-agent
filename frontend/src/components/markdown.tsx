"use client";

/**
 * Markdown 渲染组件：react-markdown + GFM（表格 / 任务列表 / 删除线）。
 * 排版样式统一走 globals.css 的 .markdown-body，预览与详情展示共用。
 */
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

interface MarkdownProps {
  /** Markdown 源文本（空值渲染占位灰字） */
  content: string;
  className?: string;
}

export default function Markdown({ content, className }: MarkdownProps) {
  if (!content || !content.trim()) {
    return <p className={"text-sm text-slate-400 " + (className || "")}>（暂无内容）</p>;
  }
  return (
    <div className={"markdown-body " + (className || "")}>
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
    </div>
  );
}
