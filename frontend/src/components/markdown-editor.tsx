"use client";

/**
 * Markdown 编辑器：编写 / 预览双页签。
 * 编写用等宽文本域，预览实时渲染（.markdown-body 排版），
 * 解决长剧本/人格模板「盲写看不到格式」的问题。
 */
import { useState } from "react";
import { Eye, PenLine } from "lucide-react";
import { cn } from "@/lib/utils";
import Markdown from "./markdown";

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  /** 编辑区 / 预览区最小高度（px），默认 280 */
  minHeight?: number;
}

/** 页签按钮（编写 / 预览） */
function TabButton({
  active,
  label,
  icon: Icon,
  onClick,
}: {
  active: boolean;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors",
        active
          ? "bg-white text-slate-800 shadow-sm ring-1 ring-slate-200"
          : "text-slate-500 hover:text-slate-700"
      )}
    >
      <Icon className="h-3.5 w-3.5" />
      {label}
    </button>
  );
}

export default function MarkdownEditor({
  value,
  onChange,
  placeholder,
  minHeight = 280,
}: MarkdownEditorProps) {
  const [tab, setTab] = useState<"write" | "preview">("write");
  return (
    <div className="overflow-hidden rounded-lg border bg-background focus-within:ring-2 focus-within:ring-ring/40">
      {/* 工具栏：编写 / 预览切换 */}
      <div className="flex items-center gap-1 border-b bg-slate-50/80 px-2 py-1.5">
        <TabButton active={tab === "write"} label="编写" icon={PenLine} onClick={() => setTab("write")} />
        <TabButton active={tab === "preview"} label="预览" icon={Eye} onClick={() => setTab("preview")} />
        <span className="ml-auto pr-2 text-xs text-slate-400">支持 Markdown 语法</span>
      </div>
      {tab === "write" ? (
        <textarea
          className="w-full resize-y border-0 bg-transparent px-4 py-3 font-mono text-sm leading-relaxed outline-none placeholder:text-muted-foreground"
          style={{ minHeight }}
          value={value}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
        />
      ) : (
        <div className="overflow-auto px-4 py-3" style={{ minHeight }}>
          <Markdown content={value} />
        </div>
      )}
    </div>
  );
}
