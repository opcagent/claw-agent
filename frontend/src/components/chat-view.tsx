"use client";

/**
 * 聊天视图：SSE 流式对话 + 多模态附件 + HITL 审批 + 会话管理。
 * 行为迁移自旧前端 chat.js：
 * - text 事件增量追加到目标气泡（打字机效果）；
 * - tool_start/tool_end/subagent/error 渲染为过程行；
 * - confirm_request 渲染审批卡片，允许/拒绝走 /api/chat/confirm 恢复执行。
 */
import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { toast } from "sonner";
import { AlertTriangle, Archive, ArchiveRestore, Bot, Brain, Check, ChevronDown, ChevronUp, Clipboard, Copy, Download, FileText, LayoutGrid, Loader2, Paperclip, Pencil, Plus, RefreshCw, RotateCcw, Search, Send, Slash, Square, Table, Trash2, User, Wrench, X, ZoomIn } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Badge } from "@/components/ui/badge";
import Markdown from "@/components/markdown";
import { api, getToken } from "@/lib/api";
import type { AgentPipeline, AgentPreset, ChatEvent, ChatMessage, ChatSession, PendingToolCall, QuickPhrase } from "@/lib/types";

/* ---------------- 数据模型 ---------------- */

type Block =
  | { id: string; kind: "user"; text: string; imageFiles: string[]; docFiles: string[]; createTime?: string }
  | { 
      id: string; 
      kind: "assistant"; 
      text: string; 
      thinking: boolean; 
      /** 思考过程文本（可折叠展示） */
      thinkingText: string;
      /** 思考区域是否展开（默认折叠） */
      thinkingExpanded: boolean;
      error?: boolean; 
      createTime?: string;
      /** 工具调用记录（不单独渲染，仅在气泡内折叠展示） */
      toolCalls?: Array<{ name: string; status: 'running' | 'success' | 'error'; callId?: string }>;
      /** 流水线进度（progress 事件时更新） */
      pipelineProgress?: { step: number; total: number; label: string };
    }
  | {
      id: string;
      kind: "hitl";
      sessionId: string;
      toolCalls: PendingToolCall[];
      status: "pending" | "approved" | "denied";
      createTime?: string;
    };

interface PendingFile {
  file: File;
  previewUrl: string | null;
}

/** 上传扩展名白名单（与后端 claw.upload.allowed-extensions 对齐，后端为准强拦截） */
const ALLOWED_EXTENSIONS = [
  "png", "jpg", "jpeg", "gif", "webp", "bmp",
  "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
  "txt", "md", "csv", "json",
];

/** 图片扩展名（前端用于区分预览 vs 下载展示） */
const IMAGE_EXTENSIONS = new Set(["png", "jpg", "jpeg", "gif", "webp", "bmp"]);

let blockSeq = 0;
const nextId = () => `b${Date.now()}-${blockSeq++}`;

/** 从存储文件名提取扩展名 */
function getFileExt(fileName: string): string {
  const dot = fileName.lastIndexOf('.');
  return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : '';
}

/** 判断存储文件名是否为图片 */
function isImageFile(fileName: string): boolean {
  return IMAGE_EXTENSIONS.has(getFileExt(fileName));
}

/** 构建附件下载/预览 URL */
function buildFileUrl(fileName: string): string {
  return `/api/upload/download?fileName=${encodeURIComponent(fileName)}`;
}

/**
 * 带 JWT 获取图片并转为 blob URL（<img> 标签无法携带自定义请求头，
 * 后端 /api/upload/download 有鉴权，必须用 fetch + Authorization 头获取）。
 * 调用方负责在不再需要时 URL.revokeObjectURL 释放内存。
 */
async function fetchAuthImageBlobUrl(fileName: string): Promise<string> {
  const token = getToken();
  const resp = await fetch(buildFileUrl(fileName), {
    headers: token ? { Authorization: "Bearer " + token } : {},
  });
  if (!resp.ok) throw new Error(`图片加载失败（HTTP ${resp.status}）`);
  const blob = await resp.blob();
  return URL.createObjectURL(blob);
}

/**
 * 鉴权图片组件：自动 fetch + blob URL 渲染，卸载时释放内存。
 * 解决 <img src> 无法携带 JWT 导致后端 401 的问题。
 */
function AuthImage({
  fileName,
  alt,
  className,
  onClick,
}: {
  fileName: string;
  alt: string;
  className?: string;
  onClick?: () => void;
}) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;
    fetchAuthImageBlobUrl(fileName)
      .then((url) => {
        if (cancelled) {
          URL.revokeObjectURL(url);
        } else {
          objectUrl = url;
          setBlobUrl(url);
        }
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fileName]);

  if (error) {
    return (
      <div className={`flex items-center justify-center rounded bg-white/10 text-xs text-white/60 ${className || ""}`}>
        图片加载失败
      </div>
    );
  }
  if (!blobUrl) {
    return <div className={`animate-pulse rounded bg-white/10 ${className || ""}`} style={{ minHeight: 48 }} />;
  }
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={blobUrl} alt={alt} className={className} onClick={onClick} />;
}

/** 从存储文件名提取原始显示名（去掉 UUID_ 前缀） */
function extractDisplayName(storedName: string): string {
  const u = storedName.indexOf('_');
  return u >= 0 ? storedName.substring(u + 1) : storedName;
}

/**
 * 去除 Markdown 标记，返回纯文本（用于复制）。
 * 仅处理常见行内/块级标记，不追求完整解析，目标是「复制出来能直接粘贴使用」。
 */
function stripMarkdown(text: string): string {
  return text
    // 代码块：```xxx ... ``` → 保留内部内容
    .replace(/```[\w]*\n?([\s\S]*?)```/g, '$1')
    // 行内代码：`code` → code
    .replace(/`([^`]+)`/g, '$1')
    // 标题：# / ## / ### → 去掉 # 前缀
    .replace(/^#{1,6}\s+/gm, '')
    // 加粗/斜体：**text** / *text* / __text__ / _text_
    .replace(/\*{1,2}([^*]+)\*{1,2}/g, '$1')
    .replace(/_{1,2}([^_]+)_{1,2}/g, '$1')
    // 链接：[text](url) → text
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    // 图片：![alt](url) → alt
    .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
    // 表格分隔行：|---|---| → 删除
    .replace(/^\|\s*[-:]+(\s*\|\s*[-:]+)*\s*\|?\s*$/gm, '')
    // 多余空行合并（连续 3 行以上空行压缩为 1 行）
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/** 检测 Markdown 文本中是否包含表格 */
function hasMarkdownTable(text: string): boolean {
  if (!text) return false;
  const lines = text.split('\n');
  for (let i = 0; i < lines.length - 1; i++) {
    if (lines[i].includes('|') && i + 1 < lines.length && /^\|?\s*[-:]+/.test(lines[i + 1])) {
      return true;
    }
  }
  return false;
}

/** 从 Markdown 文本中提取所有表格行 */
function extractMarkdownTables(text: string): string {
  const lines = text.split('\n');
  const tables: string[] = [];
  let currentTable: string[] = [];
  let inTable = false;
  for (const line of lines) {
    if (line.trim().startsWith('|')) {
      currentTable.push(line);
      inTable = true;
    } else if (inTable) {
      tables.push(currentTable.join('\n'));
      currentTable = [];
      inTable = false;
    }
  }
  if (currentTable.length > 0) tables.push(currentTable.join('\n'));
  return tables.join('\n\n');
}

/** 将 Markdown 表格转为 CSV 并触发下载 */
function exportTableAsCSV(markdownTables: string, filename: string) {
  const lines = markdownTables.split('\n').filter(l => l.trim().startsWith('|'));
  const csvRows: string[] = [];
  for (const line of lines) {
    if (/^\|\s*[-:]+/.test(line)) continue;
    const cells = line.split('|').slice(1, -1).map(c => c.trim());
    csvRows.push(cells.map(c => {
      const clean = c.replace(/\*\*/g, '');
      if (clean.includes(',') || clean.includes('"') || clean.includes('\n')) {
        return `"${clean.replace(/"/g, '""')}"`;
      }
      return clean;
    }).join(','));
  }
  const bom = '\uFEFF';
  const blob = new Blob([bom + csvRows.join('\n')], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

/** 入库消息 → 渲染区块（附件按图片/文档分类，均存储文件名，渲染时构建预览 URL） */
function messageToBlock(m: ChatMessage): Block {
  if (m.role === "user") {
    let imageFiles: string[] = [];
    let docFiles: string[] = [];
    if (m.attachments) {
      try {
        const parsed = JSON.parse(m.attachments);
        if (Array.isArray(parsed)) {
          const names = parsed.filter((x): x is string => typeof x === "string");
          imageFiles = names.filter(isImageFile);
          docFiles = names.filter((n) => !isImageFile(n));
        }
      } catch {
        // 附件 JSON 异常时忽略展示，不阻断历史回看
      }
    }
    return { id: nextId(), kind: "user", text: m.content || "", imageFiles, docFiles, createTime: m.createTime };
  }
  return {
    id: nextId(),
    kind: "assistant",
    text: m.content || "",
    thinking: false,
    thinkingText: "",
    thinkingExpanded: false,
    error: m.status === 0,
    createTime: m.createTime,
  };
}

/* ---------------- 组件 ---------------- */

export default function ChatView() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [blocks, setBlocks] = useState<Block[]>([]);
  const [presets, setPresets] = useState<AgentPreset[]>([]);
  const [presetCode, setPresetCode] = useState("");
  // 流水线与预设人格正交：人格决定「谁在回答」，流水线决定「按什么步骤执行」
  const [pipelines, setPipelines] = useState<AgentPipeline[]>([]);
  const [pipelineCode, setPipelineCode] = useState("");
  const [input, setInput] = useState("");
  const [pendingFiles, setPendingFiles] = useState<PendingFile[]>([]);
  const [sending, setSending] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  // 高级选项展开/折叠
  const [showAdvancedOptions, setShowAdvancedOptions] = useState(false);
  // 图片预览弹窗
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  // 表格预览弹窗（流水线结果表格放大查看）
  const [tablePreviewContent, setTablePreviewContent] = useState<string | null>(null);
  /** 正在重命名的会话ID（内联编辑） */
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");
  /** 常用语列表 + 快捷面板显隐 */
  const [phrases, setPhrases] = useState<QuickPhrase[]>([]);
  const [phrasePanelOpen, setPhrasePanelOpen] = useState(false);
  /** 是否查看已归档会话 */
  const [showArchived, setShowArchived] = useState(false);
  /** 会话搜索关键词 */
  const [sessionSearch, setSessionSearch] = useState("");
  /** 搜索结果（非空时替换侧栏会话列表） */
  const [searchResults, setSearchResults] = useState<ChatSession[] | null>(null);
  /** 搜索加载中标记 */
  const [searching, setSearching] = useState(false);
  /** 快捷指令触发按钮位置（用于 Portal 定位，避免被父容器 overflow-hidden 裁剪） */
  const phraseBtnRef = useRef<HTMLButtonElement>(null);
  const phrasePanelRef = useRef<HTMLDivElement>(null);
  const [phraseAnchor, setPhraseAnchor] = useState<{ top: number; left: number } | null>(null);

  // 面板打开时计算按钮位置，关闭时清空
  useLayoutEffect(() => {
    if (phrasePanelOpen && phraseBtnRef.current) {
      const rect = phraseBtnRef.current.getBoundingClientRect();
      setPhraseAnchor({ top: rect.top, left: rect.left });
    } else {
      setPhraseAnchor(null);
    }
  }, [phrasePanelOpen]);

  // 点击面板外部自动关闭
  useEffect(() => {
    if (!phrasePanelOpen) return;
    const handler = (e: MouseEvent) => {
      const target = e.target as Node;
      if (
        phraseBtnRef.current && !phraseBtnRef.current.contains(target) &&
        phrasePanelRef.current && !phrasePanelRef.current.contains(target)
      ) {
        setPhrasePanelOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [phrasePanelOpen]);

  const bottomRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const sessionIdRef = useRef<string | null>(null);
  sessionIdRef.current = currentSessionId;

  // SSE 文本增量 rAF 批处理：高频 text/thinking 事件累积到 ref，每帧统一 flush 到 state，
  // 避免每秒 10-30 次 setState 导致的过度 React reconciliation
  const rafIdRef = useRef<number>(0);
  const pendingTextRef = useRef("");
  const currentTargetIdRef = useRef<string>("");
  // 思考文本 rAF 批处理：与 text 共用 rAF 调度器，flush 时统一更新 state
  const pendingThinkingRef = useRef("");
  const thinkingTargetIdRef = useRef<string>("");

  // 当前 SSE 流的 AbortController：切换会话或卸载时用于中止流
  const streamAbortRef = useRef<AbortController | null>(null);
  // 用户主动点击「取消」标记（区分取消 vs 切换会话导致的 AbortError）
  const userCancelledRef = useRef(false);
  // 当前活跃的流 assistantId（finally 中据此判断是否应修改 state，防止旧流干扰新流）
  const activeStreamIdRef = useRef<string | null>(null);
  // 已被 cancelStream 同步清理过的 assistantId（finally 据此跳过，避免重复操作或恢复已删气泡）
  const cancelledIdsRef = useRef<Set<string>>(new Set());

  /** 中止当前 SSE 流并清理 rAF 缓冲区（切换会话/卸载时调用） */
  const abortCurrentStream = useCallback(() => {
    // 中止 SSE 流
    if (streamAbortRef.current) {
      streamAbortRef.current.abort();
      streamAbortRef.current = null;
    }
    // 取消 rAF 并清空缓冲区，防止文本泄漏到新会话
    if (rafIdRef.current) {
      cancelAnimationFrame(rafIdRef.current);
      rafIdRef.current = 0;
    }
    pendingTextRef.current = "";
    currentTargetIdRef.current = "";
    pendingThinkingRef.current = "";
    thinkingTargetIdRef.current = "";
    // 切换会话/卸载时不再需要保留活跃流标记
    activeStreamIdRef.current = null;
  }, []);

  /** 用户点击「取消」：同步移除气泡 + 中止流（不等异步 Promise settle） */
  const cancelStream = useCallback(() => {
    userCancelledRef.current = true;
    // 同步移除助手气泡，确保 UI 立即更新（api.stream 的 Promise 可能延迟 settle）
    const cancelledId = activeStreamIdRef.current;
    if (cancelledId) {
      cancelledIdsRef.current.add(cancelledId);
      setBlocks((prev) => prev.filter((b) => b.id !== cancelledId));
    }
    abortCurrentStream();
  }, [abortCurrentStream]);

  /** 将缓冲区的文本增量在下一帧统一写入 blocks state */
  const flushPendingText = useCallback(() => {
    const text = pendingTextRef.current;
    const blockId = currentTargetIdRef.current;
    if (!text || !blockId) return;
    pendingTextRef.current = "";
    setBlocks((prev) =>
      prev.map((b) => {
        if (b.id !== blockId || b.kind !== "assistant") return b;
        return {
          ...b,
          thinking: false,
          text: (b.thinking ? "" : b.text) + text,
        };
      })
    );
  }, []);

  /** 将缓冲区的思考文本在下一帧统一写入 blocks state */
  const flushPendingThinking = useCallback(() => {
    const text = pendingThinkingRef.current;
    const blockId = thinkingTargetIdRef.current;
    if (!text || !blockId) return;
    pendingThinkingRef.current = "";
    setBlocks((prev) =>
      prev.map((b) => {
        if (b.id !== blockId || b.kind !== "assistant") return b;
        return {
          ...b,
          thinkingText: (b.thinkingText || "") + text,
        };
      })
    );
  }, []);

  // Textarea 自动调整高度（最多 8 行）
  useEffect(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    ta.style.height = "auto";
    const lineHeight = parseInt(getComputedStyle(ta).lineHeight) || 20;
    const maxHeight = lineHeight * 8;
    ta.style.height = `${Math.min(ta.scrollHeight, maxHeight)}px`;
  }, [input]);

  /* ---------------- 数据加载 ---------------- */

  const loadSessions = useCallback(async () => {
    try {
      const res = await api.get<ChatSession[]>(`/api/chat/sessions?archived=${showArchived}`);
      setSessions(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载会话失败");
    }
  }, [showArchived]);

  /** 搜索会话（防抖 300ms） */
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const doSearchSessions = useCallback(async (keyword: string) => {
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    if (!keyword.trim()) {
      setSearchResults(null);
      setSearching(false);
      return;
    }
    setSearching(true);
    searchTimerRef.current = setTimeout(async () => {
      try {
        const res = await api.get<ChatSession[]>(`/api/chat/sessions/search?keyword=${encodeURIComponent(keyword.trim())}`);
        setSearchResults(res.data || []);
      } catch {
        setSearchResults([]);
      } finally {
        setSearching(false);
      }
    }, 300);
  }, []);

  useEffect(() => {
    loadSessions();
    api
      .get<AgentPreset[]>("/api/presets")
      .then((res) => setPresets(res.data || []))
      .catch(() => {});
    api
      .get<AgentPipeline[]>("/api/pipelines")
      .then((res) => setPipelines(res.data || []))
      .catch(() => {});
    api
      .get<QuickPhrase[]>("/api/phrase/list")
      .then((res) => setPhrases(res.data || []))
      .catch(() => {});
  }, [loadSessions]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [blocks]);

  // 组件卸载时中止正在进行的 SSE 流
  useEffect(() => {
    return () => {
      abortCurrentStream();
    };
  }, [abortCurrentStream]);

  /* ---------------- 区块操作 ---------------- */

  function appendBlock(block: Block) {
    setBlocks((prev) => [...prev, block]);
  }

  /** 格式化时间为 HH:mm:ss */
  function formatTime(isoString?: string): string {
    if (!isoString) return "";
    try {
      const date = new Date(isoString);
      const hours = String(date.getHours()).padStart(2, "0");
      const minutes = String(date.getMinutes()).padStart(2, "0");
      const seconds = String(date.getSeconds()).padStart(2, "0");
      return `${hours}:${minutes}:${seconds}`;
    } catch {
      return "";
    }
  }

  function updateAssistant(blockId: string, fn: (b: Extract<Block, { kind: "assistant" }>) => void) {
    setBlocks((prev) =>
      prev.map((b) => {
        if (b.id !== blockId || b.kind !== "assistant") return b;
        const copy = { ...b };
        fn(copy);
        return copy;
      })
    );
  }

  /* ---------------- SSE 事件处理 ---------------- */

  function handleEvent(event: ChatEvent, targetBlockId: string) {
    // 流已被用户取消 → 忽略所有后续事件，防止已移除的气泡被重新操作
    if (cancelledIdsRef.current.has(targetBlockId)) return;
    switch (event.type) {
      case "start":
        if (event.sessionId) setCurrentSessionId(event.sessionId);
        break;
      case "thinking_start":
        // 模型开始思考：标记思考状态
        updateAssistant(targetBlockId, (b) => {
          b.thinking = true;
        });
        break;
      case "thinking":
        // rAF 批处理：思考增量累积到 ref，与 text 共用 rAF 调度器统一 flush
        thinkingTargetIdRef.current = targetBlockId;
        pendingThinkingRef.current += event.delta || "";
        break;
      case "thinking_end":
        // 思考结束：先 flush 缓冲区中残留的思考文本，再关闭思考状态
        flushPendingThinking();
        updateAssistant(targetBlockId, (b) => {
          b.thinking = false;
          b.thinkingExpanded = false;
        });
        break;
      case "text":
        // rAF 批处理：增量累积到 ref，每帧统一 flush，避免高频 setState
        currentTargetIdRef.current = targetBlockId;
        pendingTextRef.current += event.delta || "";
        if (!rafIdRef.current) {
          rafIdRef.current = requestAnimationFrame(() => {
            rafIdRef.current = 0;
            // 同一帧内同时 flush 文本和思考增量，减少 React reconciliation 次数
            flushPendingText();
            flushPendingThinking();
          });
        }
        // 注意：不在此处 setSending(false)，保持「取消」按钮直到流真正结束
        break;
      case "tool_start":
        // 记录工具调用开始（不创建独立区块，只在助手气泡内展示）
        // #2841 容错：toolName 和 toolCallId 都可能为空（JSON 解析失败时），使用默认名称
        updateAssistant(targetBlockId, (b) => {
          if (!b.toolCalls) b.toolCalls = [];
          const toolName = event.toolName || event.toolCallId || "未知工具";
          b.toolCalls.push({ name: toolName, status: 'running', callId: event.toolCallId });
        });
        break;
      case "tool_end":
        // 更新工具调用状态（不创建独立区块）
        const statusIcon = event.state === "SUCCESS" ? "✅" : "❌";
        const statusText = event.state === "SUCCESS" ? "成功" : (event.state || "失败");
        updateAssistant(targetBlockId, (b) => {
          if (!b.toolCalls) b.toolCalls = [];
          // 查找对应的运行中工具并更新状态
          const runningTool = b.toolCalls.find(t => t.status === 'running' && (t.callId === event.toolCallId || t.name === event.toolName));
          if (runningTool) {
            runningTool.status = event.state === "SUCCESS" ? 'success' : 'error';
          } else {
            // #2841 容错：如果没有找到运行中的工具，创建新记录（使用默认名称防止空白）
            const fallbackName = event.toolName || event.toolCallId || "未知工具";
            b.toolCalls.push({ 
              name: `${statusIcon} ${fallbackName}`, 
              status: event.state === "SUCCESS" ? 'success' : 'error',
              callId: event.toolCallId 
            });
          }
        });
        break;
      case "subagent":
        // 子 Agent 调用也记录在助手气泡内
        updateAssistant(targetBlockId, (b) => {
          if (!b.toolCalls) b.toolCalls = [];
          b.toolCalls.push({ name: `🤖 ${event.label || event.subagentId || "子 Agent"}`, status: 'success' });
        });
        break;
      case "confirm_request":
        appendBlock({
          id: nextId(),
          kind: "hitl",
          sessionId: event.sessionId || sessionIdRef.current || "",
          toolCalls: event.pendingToolCalls || [],
          status: "pending",
        });
        break;
      case "error":
        // 错误只展示一次：写入助手气泡（专属错误卡片）并结束思考态，
        // 避免气泡永久停留在「思考中…」造成「无提示卡死」的观感；
        // 不再追加 ❌ 过程行与 toast（此前同一错误展示三遍，显得重复且杂乱）
        {
          const message = event.message || "执行异常，请稍后重试";
          updateAssistant(targetBlockId, (b) => {
            b.thinking = false;
            b.error = true;
            b.text = message;
          });
          // 如果是 HITL 状态残留错误，提示用户创建新会话
          if (message.includes("human-in-the-loop") || message.includes("ASKING state")) {
            toast.warning("检测到未完成的审批状态，建议点击「+ 新会话」重新开始对话", {
              duration: 5000,
            });
          }
        }
        break;
      case "end":
        // 回合结束兜底：部分模型不发 thinking_end（如不支持思考的模型），
        // 必须在此确保思考态关闭，否则 UI 永久停留在「思考中…」
        // 同时 flush rAF 队列中可能残留的文本和思考增量
        if (rafIdRef.current) {
          cancelAnimationFrame(rafIdRef.current);
          rafIdRef.current = 0;
        }
        {
          const remaining = pendingTextRef.current;
          pendingTextRef.current = "";
          currentTargetIdRef.current = "";
          const remainingThink = pendingThinkingRef.current;
          pendingThinkingRef.current = "";
          thinkingTargetIdRef.current = "";
          updateAssistant(targetBlockId, (b) => {
            b.thinking = false;
            if (remaining) {
              b.text = b.text + remaining;
            }
            if (remainingThink) {
              b.thinkingText = (b.thinkingText || "") + remainingThink;
            }
          });
        }
        break;
      case "progress":
        // 流水线进度：更新助手气泡内的进度条
        if (event.progressStep != null && event.progressTotal != null) {
          updateAssistant(targetBlockId, (b) => {
            b.pipelineProgress = {
              step: event.progressStep!,
              total: event.progressTotal!,
              label: event.progressLabel || "流水线",
            };
          });
        }
        break;
      case "quota_warn":
        // Token 配额告警（非阻断）：显示 Toast 提醒用户
        toast.warning(event.message || "本月 Token 用量较高，请注意控制使用");
        break;
      default:
        break; // ignore 等其它事件
    }
  }

  /* ---------------- 发送 ---------------- */

  async function send() {
    // 如果已有流在运行（文本已到达但 Agent 仍在后台处理），先中止旧流
    if (streamAbortRef.current) {
      userCancelledRef.current = true;
      abortCurrentStream();
      // 移除旧流未完成的助手气泡
      if (activeStreamIdRef.current) {
        const oldId = activeStreamIdRef.current;
        setBlocks((prev) => prev.filter((b) => b.id !== oldId));
      }
    }
    const content = input.trim();
    if (!content && pendingFiles.length === 0) return;

    // 先上传附件，拿到存储名
    const attachments: string[] = [];
    try {
      for (const pf of pendingFiles) {
        const r = await api.upload(pf.file);
        attachments.push(r.fileName);
      }
    } catch (e) {
      toast.error("附件上传失败：" + (e instanceof Error ? e.message : String(e)));
      return;
    }

    // 渲染用户消息（附件按图片/文档分类，使用存储文件名以便构建下载 URL）
    const userTime = new Date().toISOString();
    appendBlock({
      id: nextId(),
      kind: "user",
      text: content,
      imageFiles: attachments.filter((n) => isImageFile(n)),
      docFiles: attachments.filter((n) => !isImageFile(n)),
      createTime: userTime,
    });
    setInput("");
    setPendingFiles([]);

    const assistantId = nextId();
    const assistantTime = new Date().toISOString();
    appendBlock({ id: assistantId, kind: "assistant", text: "", thinking: true, thinkingText: "", thinkingExpanded: false, createTime: assistantTime });
    activeStreamIdRef.current = assistantId;
    setSending(true);
    // SSE 流式请求超时保护：10 分钟无响应自动中止，避免 UI 永久卡在「回复中…」
    // 流水线场景（25 轮迭代 + 多工具调用）可能持续 10-15 分钟，10 分钟作为安全网
    const streamController = new AbortController();
    streamAbortRef.current = streamController; // 存储到 ref，切换会话时可中止
    const streamTimeout = setTimeout(() => streamController.abort(), 600_000);
    try {
      await api.stream(
        "/api/chat/stream",
        {
          sessionId: sessionIdRef.current,
          content,
          presetCode: presetCode || null,
          pipelineCode: pipelineCode || null,
          attachments: attachments.length ? attachments : null,
        },
        (event) => handleEvent(event, assistantId),
        streamController.signal
      );
    } catch (e) {
      // 已被 cancelStream 同步清理 → 跳过 catch 处理
      if (cancelledIdsRef.current.has(assistantId)) {
        // no-op
      } else if (e instanceof DOMException && e.name === "AbortError") {
        // 切换会话导致的中止：气泡已由 openSession/newSession 处理
      } else {
        // 其他错误：在气泡内展示错误信息
        const message = e instanceof Error ? e.message : "对话执行失败";
        updateAssistant(assistantId, (b) => {
          b.thinking = false;
          b.error = true;
          b.text = message;
        });
      }
    } finally {
      clearTimeout(streamTimeout);
      streamAbortRef.current = null;
      // 已被 cancelStream 同步清理 → 仅做基础收尾
      if (cancelledIdsRef.current.has(assistantId)) {
        cancelledIdsRef.current.delete(assistantId);
        userCancelledRef.current = false;
      } else if (activeStreamIdRef.current === assistantId) {
        // 正常结束：清 thinking 标记
        updateAssistant(assistantId, (b) => {
          b.thinking = false;
        });
        activeStreamIdRef.current = null;
      }
      setSending(false);
      // 延迟加载会话列表，确保按钮状态先更新
      setTimeout(loadSessions, 0);
    }
  }

  /* ---------------- HITL 审批 ---------------- */

  async function doConfirm(hitlBlock: Extract<Block, { kind: "hitl" }>, approved: boolean) {
    setBlocks((prev) =>
      prev.map((b) =>
        b.id === hitlBlock.id && b.kind === "hitl"
          ? { ...b, status: approved ? "approved" : "denied" }
          : b
      )
    );
    const assistantId = nextId();
    const confirmTime = new Date().toISOString();
    appendBlock({ id: assistantId, kind: "assistant", text: "", thinking: true, thinkingText: "", thinkingExpanded: false, createTime: confirmTime });
    activeStreamIdRef.current = assistantId;
    setSending(true);
    const confirmController = new AbortController();
    streamAbortRef.current = confirmController; // 存储到 ref，切换会话时可中止
    const confirmTimeout = setTimeout(() => confirmController.abort(), 600_000);
    try {
      await api.stream(
        "/api/chat/confirm",
        { sessionId: hitlBlock.sessionId, approved },
        (event) => handleEvent(event, assistantId),
        confirmController.signal
      );
    } catch (e) {
      // 已被 cancelStream 同步清理 → 跳过
      if (cancelledIdsRef.current.has(assistantId)) {
        // no-op
      } else if (e instanceof DOMException && e.name === "AbortError") {
        // 切换会话导致的中止
      } else {
        const message = e instanceof Error ? e.message : "恢复执行失败";
        updateAssistant(assistantId, (b) => {
          b.thinking = false;
          b.error = true;
          b.text = message;
        });
      }
    } finally {
      clearTimeout(confirmTimeout);
      streamAbortRef.current = null;
      // 已被 cancelStream 同步清理 → 仅做基础收尾
      if (cancelledIdsRef.current.has(assistantId)) {
        cancelledIdsRef.current.delete(assistantId);
        userCancelledRef.current = false;
      } else if (activeStreamIdRef.current === assistantId) {
        updateAssistant(assistantId, (b) => {
          b.thinking = false;
        });
        activeStreamIdRef.current = null;
      }
      setSending(false);
      // 延迟加载会话列表，确保按钮状态先更新
      setTimeout(loadSessions, 0);
    }
  }

  /** 重新生成：删除最后一轮助手回复，重新发送最后一条用户消息 */
  async function regenerateLastReply() {
    const sid = sessionIdRef.current;
    if (!sid || sending) return;
    // 中止旧流（如果有）
    if (streamAbortRef.current) {
      userCancelledRef.current = true;
      abortCurrentStream();
      if (activeStreamIdRef.current) {
        const oldId = activeStreamIdRef.current;
        setBlocks((prev) => prev.filter((b) => b.id !== oldId));
      }
    }
    // 移除最后一个助手气泡（即将被重新生成）
    const lastAssistantIdx = [...blocks].map((b, i) => b.kind === "assistant" ? i : -1).filter(i => i >= 0).pop();
    if (lastAssistantIdx !== undefined) {
      const removedId = blocks[lastAssistantIdx].id;
      setBlocks((prev) => prev.filter((b) => b.id !== removedId));
    }
    const assistantId = nextId();
    appendBlock({ id: assistantId, kind: "assistant", text: "", thinking: true, thinkingText: "", thinkingExpanded: false, createTime: new Date().toISOString() });
    activeStreamIdRef.current = assistantId;
    setSending(true);
    const ctrl = new AbortController();
    streamAbortRef.current = ctrl;
    const timeout = setTimeout(() => ctrl.abort(), 600_000);
    try {
      await api.stream(
        `/api/chat/regenerate?sessionId=${encodeURIComponent(sid)}`,
        {},
        (event) => handleEvent(event, assistantId),
        ctrl.signal
      );
    } catch (e) {
      if (cancelledIdsRef.current.has(assistantId)) {
        // no-op
      } else if (e instanceof DOMException && e.name === "AbortError") {
        // 切换会话导致
      } else {
        const message = e instanceof Error ? e.message : "重新生成失败";
        updateAssistant(assistantId, (b) => {
          b.thinking = false;
          b.error = true;
          b.text = message;
        });
      }
    } finally {
      clearTimeout(timeout);
      streamAbortRef.current = null;
      if (cancelledIdsRef.current.has(assistantId)) {
        cancelledIdsRef.current.delete(assistantId);
        userCancelledRef.current = false;
      } else if (activeStreamIdRef.current === assistantId) {
        updateAssistant(assistantId, (b) => { b.thinking = false; });
        activeStreamIdRef.current = null;
      }
      setSending(false);
      setTimeout(loadSessions, 0);
    }
  }

  /* ---------------- 会话操作 ---------------- */

  function newSession() {
    // 中止正在进行的 SSE 流，清理 rAF 缓冲区
    abortCurrentStream();
    setSending(false);
    setCurrentSessionId(null);
    setBlocks([]);
  }

  /** 打开历史会话：从后端拉取已入库的聊天记录回看 */
  async function openSession(sessionId: string) {
    // 如果打开的是当前会话，不做任何操作
    if (sessionId === currentSessionId) return;
    // 中止正在进行的 SSE 流，清理 rAF 缓冲区
    abortCurrentStream();
    setSending(false);
    setCurrentSessionId(sessionId);
    setBlocks([
      {
        id: nextId(),
        kind: "assistant",
        text: "—— 正在加载历史聊天记录… ——",
        thinking: false,
        thinkingText: "",
        thinkingExpanded: false,
      },
    ]);
    try {
      const res = await api.get<ChatMessage[]>(
        `/api/chat/sessions/${encodeURIComponent(sessionId)}/messages`
      );
      const messages = res.data || [];
      if (messages.length === 0) {
        setBlocks([
          {
            id: nextId(),
            kind: "assistant",
            text: "—— 该会话暂无已入库的历史消息，继续对话吧 ——",
            thinking: false,
            thinkingText: "",
            thinkingExpanded: false,
          },
        ]);
        return;
      }
      setBlocks(messages.map(messageToBlock));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载聊天记录失败");
      setBlocks([]);
    }
  }

  async function deleteSession(s: ChatSession) {
    if (!window.confirm("删除该会话？")) return;
    try {
      await api.del("/api/chat/sessions/" + encodeURIComponent(s.sessionId));
      if (currentSessionId === s.sessionId) newSession();
      loadSessions();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  /** 归档会话 */
  async function archiveSession(s: ChatSession) {
    try {
      await api.put(`/api/chat/sessions/${encodeURIComponent(s.sessionId)}/archive`);
      if (currentSessionId === s.sessionId) newSession();
      loadSessions();
      toast.success("会话已归档");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "归档失败");
    }
  }

  /** 取消归档 */
  async function unarchiveSession(s: ChatSession) {
    try {
      await api.put(`/api/chat/sessions/${encodeURIComponent(s.sessionId)}/unarchive`);
      loadSessions();
      toast.success("会话已恢复");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "恢复失败");
    }
  }

  /** 开始重命名会话 */
  function startRename(s: ChatSession) {
    setRenamingId(s.sessionId);
    setRenameValue(s.title || "");
  }

  /** 提交重命名 */
  async function submitRename(s: ChatSession) {
    if (!renameValue.trim()) {
      toast.error("标题不能为空");
      return;
    }
    try {
      await api.put(`/api/chat/sessions/${encodeURIComponent(s.sessionId)}/title`, {
        title: renameValue.trim(),
      });
      setRenamingId(null);
      loadSessions();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "重命名失败");
    }
  }

  /** 取消重命名 */
  function cancelRename() {
    setRenamingId(null);
    setRenameValue("");
  }

  /** 导出会话为 Markdown 文件 */
  async function exportSession(s: ChatSession) {
    try {
      const token = getToken();
      const resp = await fetch(`/api/chat/sessions/${encodeURIComponent(s.sessionId)}/export?format=markdown`, {
        headers: token ? { Authorization: "Bearer " + token } : {},
      });
      if (!resp.ok) throw new Error(`导出失败（HTTP ${resp.status}）`);
      const blob = await resp.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${s.title || s.sessionId}.md`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "导出失败");
    }
  }

  function onFilesSelected(list: FileList | null) {
    if (!list) return;
    // 与后端扩展名白名单对齐：不合规文件提前拦截，避免上传后才报错
    const accepted: PendingFile[] = [];
    for (const file of Array.from(list)) {
      const ext = file.name.includes(".") ? file.name.split(".").pop()!.toLowerCase() : "";
      if (!ALLOWED_EXTENSIONS.includes(ext)) {
        toast.error(`不支持的文件类型：${file.name}`);
        continue;
      }
      accepted.push({
        file,
        previewUrl: file.type.startsWith("image/") ? URL.createObjectURL(file) : null,
      });
    }
    if (accepted.length > 0) setPendingFiles((prev) => [...prev, ...accepted]);
    if (fileInputRef.current) fileInputRef.current.value = "";
  }

  /** 复制文本到剪贴板（useCallback 稳定引用，避免子组件不必要的重渲染） */
  const copyToClipboard = useCallback(async (text: string, label: string = "内容") => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(`${label}已复制到剪贴板`);
    } catch (e) {
      toast.error("复制失败");
    }
  }, []);


  /* ---------------- 渲染 ---------------- */

  return (
    <div className="flex h-full w-full overflow-hidden">
      {/* 会话侧栏 - 移动端隐藏 */}
      <div className="hidden md:flex w-60 shrink-0 flex-col border-r border-slate-200/70 bg-white/60 overflow-hidden">
        <div className="p-3">
          <Button variant="outline" className="w-full justify-start gap-2" onClick={newSession}>
            <Plus className="h-4 w-4" /> 新会话
          </Button>
        </div>
        {/* 会话搜索框 */}
        {!showArchived && (
          <div className="relative px-3 pb-2">
            <Search className="absolute left-5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground/60" />
            <input
              className="w-full rounded-md border bg-background py-1.5 pl-8 pr-3 text-xs outline-none focus:border-indigo-400 transition-colors"
              placeholder="搜索会话内容…"
              value={sessionSearch}
              onChange={(e) => {
                setSessionSearch(e.target.value);
                doSearchSessions(e.target.value);
              }}
            />
            {searching && <Loader2 className="absolute right-5 top-1/2 h-3 w-3 -translate-y-1/2 animate-spin text-muted-foreground/60" />}
          </div>
        )}
        {/* 会话/归档 Tab 切换 */}
        <div className="flex items-center gap-1 px-3 pb-2">
          <button
            className={`flex-1 rounded-md px-2 py-1 text-xs font-medium transition-colors ${
              !showArchived ? "bg-indigo-100 text-indigo-600" : "text-slate-500 hover:bg-slate-100"
            }`}
            onClick={() => setShowArchived(false)}
          >
            会话
          </button>
          <button
            className={`flex-1 rounded-md px-2 py-1 text-xs font-medium transition-colors ${
              showArchived ? "bg-indigo-100 text-indigo-600" : "text-slate-500 hover:bg-slate-100"
            }`}
            onClick={() => setShowArchived(true)}
          >
            归档
          </button>
        </div>
        <ScrollArea className="flex-1 min-h-0">
          <div className="space-y-1 px-3 pb-3">
            {(searchResults !== null ? searchResults : sessions).map((s) => (
              <div
                key={s.sessionId}
                className={
                  "group flex cursor-pointer items-center justify-between rounded-lg px-2 py-2 text-sm " +
                  (s.sessionId === currentSessionId
                    ? "bg-indigo-50 text-indigo-600"
                    : "hover:bg-slate-100")
                }
                onClick={() => !showArchived && renamingId !== s.sessionId && openSession(s.sessionId)}
              >
                {renamingId === s.sessionId && !showArchived ? (
                  <input
                    className="flex-1 min-w-0 rounded border border-indigo-300 bg-white px-1.5 py-0.5 text-sm outline-none focus:border-indigo-500"
                    value={renameValue}
                    autoFocus
                    onChange={(e) => setRenameValue(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") submitRename(s);
                      if (e.key === "Escape") cancelRename();
                    }}
                    onBlur={() => submitRename(s)}
                    onClick={(e) => e.stopPropagation()}
                    maxLength={60}
                  />
                ) : (
                  <span className="truncate" title={s.summary || s.title || "新会话"}>
                    {s.title || "新会话"}
                  </span>
                )}
                {renamingId !== s.sessionId && (
                  <div className="flex shrink-0 items-center gap-1">
                    {showArchived ? (
                      <>
                        <ArchiveRestore
                          className="h-3.5 w-3.5 text-muted-foreground hover:text-indigo-500 cursor-pointer"
                          onClick={(e) => {
                            e.stopPropagation();
                            unarchiveSession(s);
                          }}
                        />
                        <Trash2
                          className="h-3.5 w-3.5 text-muted-foreground hover:text-destructive"
                          onClick={(e) => {
                            e.stopPropagation();
                            deleteSession(s);
                          }}
                        />
                      </>
                    ) : (
                      <>
                        <Pencil
                          className="h-3.5 w-3.5 text-muted-foreground hover:text-indigo-500"
                          onClick={(e) => {
                            e.stopPropagation();
                            startRename(s);
                          }}
                        />
                        <Download
                          className="h-3.5 w-3.5 text-muted-foreground hover:text-emerald-500 cursor-pointer"
                          onClick={(e) => {
                            e.stopPropagation();
                            exportSession(s);
                          }}
                        />
                        <Archive
                          className="h-3.5 w-3.5 text-muted-foreground hover:text-amber-500 cursor-pointer"
                          onClick={(e) => {
                            e.stopPropagation();
                            archiveSession(s);
                          }}
                        />
                      </>
                    )}
                  </div>
                )}
              </div>
            ))}
            {(searchResults !== null ? searchResults : sessions).length === 0 && (
              <p className="px-2 py-4 text-center text-xs text-muted-foreground">
                {searchResults !== null
                  ? (searchResults.length === 0 ? "未找到匹配的会话" : "")
                  : (showArchived ? "暂无归档会话" : "暂无会话，发起对话自动创建")}
              </p>
            )}
          </div>
        </ScrollArea>
      </div>

      {/* 消息区 + 输入区：min-w-0 阻断 flex 链撑宽——长不可断字符串（路径/URL）
          缺它时会把整行撑超视口，挤掉左侧会话侧栏 */}
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        {/* 移动端顶部工具栏 */}
        <div className="md:hidden flex items-center gap-2 px-3 py-2 border-b border-slate-200/70 bg-white/60">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="gap-1"
          >
            <LayoutGrid className="h-4 w-4" />
            会话
          </Button>
          <span className="text-sm text-muted-foreground truncate">
            {sessions.find(s => s.sessionId === currentSessionId)?.title || "新会话"}
          </span>
        </div>

        {/* 移动端侧栏抽屉 */}
        {sidebarOpen && (
          <div className="md:hidden absolute inset-0 z-50 bg-black/20" onClick={() => setSidebarOpen(false)}>
            <div
              className="w-64 h-full bg-white shadow-lg"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="p-3 border-b">
                <Button variant="outline" className="w-full justify-start gap-2" onClick={() => { newSession(); setSidebarOpen(false); }}>
                  <Plus className="h-4 w-4" /> 新会话
                </Button>
              </div>
              {/* 会话/归档 Tab 切换 */}
              <div className="flex items-center gap-1 px-3 py-2 border-b">
                <button
                  className={`flex-1 rounded-md px-2 py-1 text-xs font-medium transition-colors ${
                    !showArchived ? "bg-indigo-100 text-indigo-600" : "text-slate-500 hover:bg-slate-100"
                  }`}
                  onClick={() => setShowArchived(false)}
                >
                  会话
                </button>
                <button
                  className={`flex-1 rounded-md px-2 py-1 text-xs font-medium transition-colors ${
                    showArchived ? "bg-indigo-100 text-indigo-600" : "text-slate-500 hover:bg-slate-100"
                  }`}
                  onClick={() => setShowArchived(true)}
                >
                  归档
                </button>
              </div>
              <ScrollArea className="flex-1 h-[calc(100%-110px)]">
                <div className="space-y-1 px-3 pb-3">
                  {sessions.map((s) => (
                    <div
                      key={s.sessionId}
                      className={
                        "group flex cursor-pointer items-center justify-between rounded-lg px-2 py-2 text-sm " +
                        (s.sessionId === currentSessionId
                          ? "bg-indigo-50 text-indigo-600"
                          : "hover:bg-slate-100")
                      }
                      onClick={() => !showArchived && renamingId !== s.sessionId && (openSession(s.sessionId), setSidebarOpen(false))}
                    >
                      {renamingId === s.sessionId && !showArchived ? (
                        <input
                          className="flex-1 min-w-0 rounded border border-indigo-300 bg-white px-1.5 py-0.5 text-sm outline-none focus:border-indigo-500"
                          value={renameValue}
                          autoFocus
                          onChange={(e) => setRenameValue(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") submitRename(s);
                            if (e.key === "Escape") cancelRename();
                          }}
                          onBlur={() => submitRename(s)}
                          onClick={(e) => e.stopPropagation()}
                          maxLength={60}
                        />
                      ) : (
                        <span className="truncate" title={s.summary || s.title || "新会话"}>
                          {s.title || "新会话"}
                        </span>
                      )}
                      {renamingId !== s.sessionId && (
                        <div className="flex shrink-0 items-center gap-1">
                          {showArchived ? (
                            <>
                              <ArchiveRestore
                                className="h-3.5 w-3.5 text-muted-foreground hover:text-indigo-500 cursor-pointer"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  unarchiveSession(s);
                                }}
                              />
                              <Trash2
                                className="h-3.5 w-3.5 text-muted-foreground hover:text-destructive"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  deleteSession(s);
                                }}
                              />
                            </>
                          ) : (
                            <>
                              <Pencil
                                className="h-3.5 w-3.5 text-muted-foreground hover:text-indigo-500"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  startRename(s);
                                }}
                              />
                              <Download
                                className="h-3.5 w-3.5 text-muted-foreground hover:text-emerald-500 cursor-pointer"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  exportSession(s);
                                }}
                              />
                              <Archive
                                className="h-3.5 w-3.5 text-muted-foreground hover:text-amber-500 cursor-pointer"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  archiveSession(s);
                                }}
                              />
                            </>
                          )}
                        </div>
                      )}
                    </div>
                  ))}
                  {sessions.length === 0 && (
                    <p className="px-2 py-4 text-center text-xs text-muted-foreground">
                      {showArchived ? "暂无归档会话" : "暂无会话，发起对话自动创建"}
                    </p>
                  )}
                </div>
              </ScrollArea>
            </div>
          </div>
        )}

        <ScrollArea className="flex-1 min-h-0">
          <div className="mx-auto max-w-5xl space-y-4 p-4">
            {blocks.map((block, blockIdx) => (
              <BlockView 
                key={block.id} 
                block={block} 
                onConfirm={doConfirm}
                copyToClipboard={copyToClipboard}
                formatTime={formatTime}
                onPreviewImage={setPreviewImage}
                onPreviewTable={setTablePreviewContent}
                isLastAssistant={block.kind === "assistant" && !blocks.slice(blockIdx + 1).some(b => b.kind === "assistant")}
                onRegenerate={regenerateLastReply}
                canRegenerate={!sending && !!currentSessionId}
              />
            ))}
            <div ref={bottomRef} />
          </div>
        </ScrollArea>

        {/* 输入区 */}
        <div className="border-t border-slate-200/70 bg-white/70 p-3 backdrop-blur">
          <div className="mx-auto max-w-5xl space-y-2">
            {/* 附件预览 */}
            {pendingFiles.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {pendingFiles.map((pf, i) => (
                  <div
                    key={i}
                    className="flex items-center gap-1.5 rounded-md border bg-muted/50 px-2 py-1 text-xs"
                  >
                    {pf.previewUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={pf.previewUrl} alt="" className="h-8 w-8 rounded object-cover" />
                    ) : (
                      <Paperclip className="h-3.5 w-3.5" />
                    )}
                    <span className="max-w-[80px] sm:max-w-32 truncate">{pf.file.name}</span>
                    <button
                      className="text-muted-foreground hover:text-destructive"
                      onClick={() => setPendingFiles((prev) => prev.filter((_, j) => j !== i))}
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            )}
            
            {/* 高级选项折叠面板 */}
            <div className="space-y-2">
              <button
                onClick={() => setShowAdvancedOptions(!showAdvancedOptions)}
                className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
              >
                {showAdvancedOptions ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                高级选项
              </button>
              
              {showAdvancedOptions && (
                <div className="flex flex-col sm:flex-row items-end gap-2 animate-in slide-in-from-top-2 duration-200">
                  <select
                    className="h-9 rounded-md border bg-background px-2 text-sm w-full sm:w-auto"
                    value={presetCode}
                    onChange={(e) => setPresetCode(e.target.value)}
                    title="预设人格"
                  >
                    <option value="">默认人格</option>
                    {presets
                      .filter((p) => p.enabled === 1)
                      .map((p) => (
                        <option key={p.id} value={p.agentCode}>
                          {p.agentName}
                        </option>
                      ))}
                  </select>
                  {pipelines.length > 0 && (
                    <select
                      className="h-9 rounded-md border bg-background px-2 text-sm w-full sm:w-auto"
                      value={pipelineCode}
                      onChange={(e) => setPipelineCode(e.target.value)}
                      title="编排流水线"
                    >
                      <option value="">无流水线</option>
                      {pipelines.map((p) => (
                        <option key={p.id} value={p.pipelineCode}>
                          {p.pipelineName}
                        </option>
                      ))}
                    </select>
                  )}
                </div>
              )}
            </div>
            
            <div className="relative flex flex-col sm:flex-row items-end gap-2">
              {/* 常用语快捷面板：Portal 渲染到 body，避免被父容器 overflow-hidden 裁剪 */}
              {phrasePanelOpen && phraseAnchor && createPortal(
                <div
                  ref={phrasePanelRef}
                  className="fixed z-50 w-72 rounded-xl border bg-white shadow-lg flex flex-col"
                  style={{
                    top: `${Math.max(8, phraseAnchor.top - 360)}px`,
                    left: `${phraseAnchor.left}px`,
                    maxHeight: '340px',
                  }}
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="flex items-center justify-between border-b px-3 py-2 shrink-0">
                    <span className="text-sm font-medium text-slate-700">快捷指令</span>
                    <button onClick={() => setPhrasePanelOpen(false)} className="text-muted-foreground hover:text-foreground">
                      <X className="h-4 w-4" />
                    </button>
                  </div>
                  <ScrollArea className="flex-1 min-h-0">
                    <div className="space-y-1 p-2">
                      {phrases.length > 0 ? phrases.map((p) => (
                        <button
                          key={p.id}
                          className="w-full rounded-lg px-3 py-2 text-left text-sm hover:bg-slate-100 transition-colors"
                          onClick={() => {
                            setInput(p.content);
                            setPhrasePanelOpen(false);
                          }}
                        >
                          <div className="font-medium text-slate-700">{p.title}</div>
                          <div className="line-clamp-1 text-xs text-muted-foreground">{p.content}</div>
                        </button>
                      )) : (
                        <p className="py-4 text-center text-xs text-muted-foreground">暂无快捷指令</p>
                      )}
                    </div>
                  </ScrollArea>
                </div>,
                document.body
              )}
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept={ALLOWED_EXTENSIONS.map((e) => "." + e).join(",")}
                className="hidden"
                onChange={(e) => onFilesSelected(e.target.files)}
              />
              <Button
                ref={phraseBtnRef as React.RefObject<HTMLButtonElement>}
                variant="ghost"
                size="icon"
                title="快捷指令"
                onClick={() => setPhrasePanelOpen(!phrasePanelOpen)}
              >
                <Slash className="h-4 w-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                title="添加附件"
                onClick={() => fileInputRef.current?.click()}
              >
                <Paperclip className="h-4 w-4" />
              </Button>
              <Textarea
                ref={textareaRef}
                className="min-h-16 flex-1 resize-none w-full"
                rows={1}
                placeholder="输入消息，Enter 发送，Shift+Enter 换行，/ 快捷指令"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  // Enter 发送（Shift+Enter 换行）；回复中时 Enter 正常换行
                  if (e.key === "Enter" && !e.shiftKey && !sending) {
                    e.preventDefault();
                    send();
                  }
                  // / 快捷键：空输入时按 / 打开快捷指令面板
                  if (e.key === "/" && input.length === 0 && phrases.length > 0) {
                    e.preventDefault();
                    setPhrasePanelOpen(true);
                  }
                }}
              />
              {sending ? (
                <Button onClick={cancelStream} variant="outline" className="gap-1 shrink-0 border-red-200 text-red-600 hover:bg-red-50">
                  <Square className="h-3.5 w-3.5 fill-current" />
                  <span className="hidden sm:inline">取消</span>
                </Button>
              ) : (
                <Button onClick={send} className="gap-1 shrink-0">
                  <Send className="h-4 w-4" />
                  <span className="hidden sm:inline">发送</span>
                </Button>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 图片预览弹窗 */}
      {previewImage && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
          onClick={() => {
            if (previewImage.startsWith("blob:")) URL.revokeObjectURL(previewImage);
            setPreviewImage(null);
          }}
        >
          <div className="relative max-h-[90vh] max-w-[90vw]" onClick={(e) => e.stopPropagation()}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={previewImage}
              alt="预览"
              className="max-h-[85vh] max-w-full rounded-lg object-contain shadow-2xl"
            />
            <button
              onClick={() => {
                if (previewImage.startsWith("blob:")) URL.revokeObjectURL(previewImage);
                setPreviewImage(null);
              }}
              className="absolute -right-2 -top-2 flex h-8 w-8 items-center justify-center rounded-full bg-white/90 text-slate-600 shadow-md transition-colors hover:bg-white"
              title="关闭"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}

      {/* 表格预览弹窗：流水线结果表格放大查看 */}
      {tablePreviewContent && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6"
          onClick={() => setTablePreviewContent(null)}
        >
          <div
            className="relative max-h-[85vh] w-full max-w-5xl overflow-hidden rounded-xl bg-white shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b px-5 py-3">
              <div className="flex items-center gap-2">
                <Table className="h-4 w-4 text-indigo-500" />
                <span className="text-sm font-medium text-slate-700">表格预览</span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => exportTableAsCSV(tablePreviewContent, "客户开发结果.csv")}
                  className="inline-flex items-center gap-1.5 rounded-md bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-200"
                >
                  <Download className="h-3.5 w-3.5" /> 导出CSV
                </button>
                <button
                  onClick={() => setTablePreviewContent(null)}
                  className="flex h-7 w-7 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </div>
            <div className="overflow-auto p-5" style={{ maxHeight: 'calc(85vh - 56px)' }}>
              <Markdown content={tablePreviewContent} />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/* ---------------- 区块渲染 ---------------- */

/** BlockView 使用 React.memo 包裹，SSE 流式更新时只重渲染正在接收文本的 Block */
const BlockView = React.memo(function BlockView({
  block,
  onConfirm,
  copyToClipboard,
  formatTime,
  onPreviewImage,
  onPreviewTable,
  isLastAssistant,
  onRegenerate,
  canRegenerate,
}: {
  block: Block;
  onConfirm: (b: Extract<Block, { kind: "hitl" }>, approved: boolean) => void;
  copyToClipboard: (text: string, label?: string) => Promise<void>;
  formatTime: (isoString?: string) => string;
  onPreviewImage?: (url: string) => void;
  onPreviewTable?: (content: string) => void;
  /** 是否为最后一个助手气泡（显示重新生成按钮） */
  isLastAssistant?: boolean;
  onRegenerate?: () => void;
  canRegenerate?: boolean;
}) {
  if (block.kind === "user") {
    return (
      <div className="flex justify-end gap-2">
        <div className="group relative max-w-[90%] lg:max-w-[80%] space-y-1 rounded-2xl bg-gradient-to-br from-indigo-500 to-violet-500 px-4 py-2.5 pl-8 text-sm text-white shadow-sm">
          {/* 复制按钮：气泡内左上角，hover 显示 */}
          <button
            onClick={() => copyToClipboard(block.text, "消息")}
            className="absolute left-1.5 top-1.5 rounded p-1 opacity-0 transition-opacity hover:bg-white/20 group-hover:opacity-100"
            title="复制消息"
          >
            <Copy className="h-3.5 w-3.5 text-white/70" />
          </button>
          {block.text && <p className="break-words whitespace-pre-wrap pr-1">{block.text}</p>}
          {/* 图片附件：点击放大预览（AuthImage 通过 fetch+JWT 获取，解决 img 标签无法携带 token 的问题） */}
          {block.imageFiles.map((name, i) => (
            <button
              key={i}
              onClick={() => {
                fetchAuthImageBlobUrl(name)
                  .then((url) => onPreviewImage?.(url))
                  .catch(() => toast.error("图片加载失败"));
              }}
              className="block group/img relative"
              title="点击放大"
            >
              <AuthImage
                fileName={name}
                alt={extractDisplayName(name)}
                className="max-h-48 rounded transition-opacity hover:opacity-90"
              />
              <ZoomIn className="absolute right-1.5 top-1.5 h-4 w-4 text-white opacity-0 drop-shadow-md transition-opacity group-hover/img:opacity-100" />
            </button>
          ))}
          {/* 文档附件：点击跳转下载 */}
          {block.docFiles.map((name, i) => (
            <a
              key={i}
              href={buildFileUrl(name)}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 rounded bg-white/15 px-2 py-1 text-xs transition-colors hover:bg-white/25"
            >
              <FileText className="h-3.5 w-3.5 shrink-0" />
              <span className="max-w-[160px] truncate">{extractDisplayName(name)}</span>
            </a>
          ))}
          {/* 发送时间 */}
          {block.createTime && (
            <p className="text-[10px] opacity-70 text-right mt-1">
              {formatTime(block.createTime)}
            </p>
          )}
        </div>
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-indigo-100 shadow-sm">
          <User className="h-4 w-4 text-indigo-600" />
        </div>
      </div>
    );
  }

  if (block.kind === "assistant") {
    // 错误消息用专属错误卡片：图标 + 标题 + 原因 + 后续指引，一处展示不重复、视觉更精致；
    // 正常回复保持简洁气泡（去掉原来的错误态红色样式分支）
    if (block.error) {
      return (
        <div className="flex gap-2">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-500 shadow-sm">
            <Bot className="h-4 w-4 text-white" />
          </div>
          <div className="group relative max-w-[90%] lg:max-w-[80%] rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 shadow-sm">
            {/* 复制按钮：气泡内右上角，hover 显示 */}
            <button
              onClick={() => copyToClipboard(block.text, "错误信息")}
              className="absolute right-2 top-2 rounded p-1 opacity-0 transition-opacity hover:bg-rose-100 group-hover:opacity-100"
              title="复制错误信息"
            >
              <Copy className="h-3.5 w-3.5 text-rose-400" />
            </button>
            <div className="flex items-center gap-2 text-sm font-medium text-rose-600">
              <AlertTriangle className="h-4 w-4 shrink-0" />
              对话执行失败
            </div>
            <p className="mt-1.5 whitespace-pre-wrap break-words text-sm text-rose-700/90">
              {block.text}
            </p>
            <p className="mt-1.5 text-xs text-rose-400">
              可稍后重试；若问题持续，请联系管理员检查模型提供商配置
            </p>
            {/* 错误回复时间 */}
            {block.createTime && (
              <p className="text-[10px] text-rose-400/70 text-right mt-1">
                {formatTime(block.createTime)}
              </p>
            )}
          </div>
        </div>
      );
    }
    return (
      <div className="flex gap-2">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-500 shadow-sm">
          <Bot className="h-4 w-4 text-white" />
        </div>
        <div className="group relative max-w-[90%] lg:max-w-[80%]">
          {/* 工具调用标签（ChatGPT 风格：紧凑 inline 标签） */}
          {block.toolCalls && block.toolCalls.length > 0 && (
            <details className="group/tool mb-2">
              <summary className="cursor-pointer list-none inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-[11px] text-slate-500 hover:bg-slate-200 transition-colors">
                <Wrench className="h-3 w-3" />
                <span>{block.toolCalls.length} 个工具</span>
                <ChevronDown className="h-3 w-3 transition-transform group-open/tool:rotate-180" />
              </summary>
              <div className="mt-1.5 ml-1 flex flex-wrap gap-1.5">
                {block.toolCalls.map((tool, idx) => (
                  <span
                    key={idx}
                    className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] ${
                      tool.status === 'running'
                        ? 'bg-indigo-50 text-indigo-600'
                        : tool.status === 'error'
                        ? 'bg-rose-50 text-rose-600'
                        : 'bg-emerald-50 text-emerald-600'
                    }`}
                  >
                    {tool.status === 'running' && <Loader2 className="h-2.5 w-2.5 animate-spin" />}
                    {tool.status === 'success' && <Check className="h-2.5 w-2.5" />}
                    {tool.status === 'error' && <X className="h-2.5 w-2.5" />}
                    {tool.name}
                  </span>
                ))}
              </div>
            </details>
          )}

          {/* 流水线进度条（progress 事件驱动，仅流水线场景显示） */}
          {block.pipelineProgress && (
            <div className="mb-2 rounded-lg bg-indigo-50/80 px-3 py-2">
              <div className="flex items-center justify-between mb-1">
                <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-indigo-600">
                  <Loader2 className="h-3 w-3 animate-spin" />
                  {block.pipelineProgress.label}
                </span>
                <span className="text-[11px] text-indigo-500">
                  步骤 {block.pipelineProgress.step}/{block.pipelineProgress.total}
                </span>
              </div>
              <div className="h-1.5 w-full rounded-full bg-indigo-100">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-indigo-400 to-violet-500 transition-all duration-500 ease-out"
                  style={{ width: `${Math.round((block.pipelineProgress.step / block.pipelineProgress.total) * 100)}%` }}
                />
              </div>
            </div>
          )}

          {/* 思考过程（Claude 风格：左侧细线 + 浅色背景） */}
          {block.thinkingText && (
            <details
              className="group/think mb-3"
              open={block.thinking}
            >
              <summary className="cursor-pointer list-none flex items-center gap-1.5 text-[11px] text-slate-400 hover:text-slate-600 transition-colors mb-1.5">
                <Brain className="h-3.5 w-3.5" />
                <span>{block.thinking ? "思考中…" : "思考过程"}</span>
                {block.thinking && <Loader2 className="h-3 w-3 animate-spin" />}
                <ChevronDown className="h-3 w-3 transition-transform group-open/think:rotate-180" />
              </summary>
              <div className="border-l-2 border-slate-200 pl-3 text-xs text-slate-500 whitespace-pre-wrap break-words max-h-48 overflow-y-auto leading-relaxed">
                {block.thinkingText}
              </div>
            </details>
          )}

          {/* 主回复气泡：有文本、思考结束、或有思考过程时显示 */}
          {(block.text || !block.thinking || block.thinkingText || block.thinking) && (
            <div className="relative rounded-2xl border border-slate-200/60 bg-white px-4 py-3 text-sm shadow-sm">
              {/* 复制按钮：气泡内右上角，hover 显示 */}
              <button
                onClick={() => copyToClipboard(stripMarkdown(block.text || ""), "回复")}
                className="absolute right-2 top-2 z-10 rounded p-1 opacity-0 transition-opacity hover:bg-slate-100 group-hover:opacity-100"
                title="复制回复"
              >
                <Copy className="h-3.5 w-3.5 text-muted-foreground" />
              </button>
              {/* 重新生成按钮：仅最后一个助手气泡显示 */}
              {isLastAssistant && canRegenerate && !block.thinking && (
                <button
                  onClick={onRegenerate}
                  className="absolute right-8 top-2 z-10 rounded p-1 opacity-0 transition-opacity hover:bg-slate-100 group-hover:opacity-100"
                  title="重新生成"
                >
                  <RefreshCw className="h-3.5 w-3.5 text-muted-foreground" />
                </button>
              )}

              {/* 表格工具栏（浮动小条） */}
              {!block.thinking && hasMarkdownTable(block.text || "") && (
                <div className="flex items-center gap-1.5 mb-2 pb-2 border-b border-dashed border-slate-200">
                  <Table className="h-3 w-3 text-slate-400" />
                  <span className="text-[11px] text-slate-400">表格</span>
                  <div className="ml-auto flex items-center gap-1">
                    <button
                      onClick={() => exportTableAsCSV(extractMarkdownTables(block.text || ""), "客户开发结果.csv")}
                      className="inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] text-slate-500 hover:bg-slate-100 transition-colors"
                    >
                      <Download className="h-2.5 w-2.5" /> 导出
                    </button>
                    <button
                      onClick={() => onPreviewTable?.(extractMarkdownTables(block.text || ""))}
                      className="inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] text-indigo-500 hover:bg-indigo-50 transition-colors"
                    >
                      <ZoomIn className="h-2.5 w-2.5" /> 预览
                    </button>
                  </div>
                </div>
              )}

              {block.text ? (
                <Markdown content={block.text} />
              ) : (
                <span className="text-muted-foreground">思考中…</span>
              )}

              {/* 回复时间 */}
              {!block.thinking && block.createTime && (
                <p className="text-[10px] text-muted-foreground/50 text-right mt-2">
                  {formatTime(block.createTime)}
                </p>
              )}
            </div>
          )}
        </div>
      </div>
    );
  }


  // HITL 审批卡片
  const disabled = block.status !== "pending";
  return (
    <div className="group relative mx-auto max-w-lg rounded-lg border border-amber-300 bg-amber-50 p-3 dark:bg-amber-950/30">
      {/* 复制按钮 */}
      <button
        onClick={() => {
          const toolInfo = block.toolCalls.map(t => `${t.toolName}: ${t.toolInput || '无参数'}`).join('\n');
          copyToClipboard(toolInfo, "工具调用信息");
        }}
        className="absolute -right-8 top-2 opacity-0 transition-opacity group-hover:opacity-100"
        title="复制工具调用信息"
      >
        <Copy className="h-4 w-4 text-muted-foreground hover:text-foreground" />
      </button>
      
      <h4 className="mb-2 text-sm font-medium">
        {block.status === "pending" && "⚠️ Agent 请求执行以下操作，是否允许？"}
        {block.status === "approved" && "✅ 已允许，继续执行…"}
        {block.status === "denied" && "🚫 已拒绝"}
      </h4>
      <div className="space-y-2">
        {block.toolCalls.map((t) => (
          <div key={t.toolCallId} className="rounded border bg-background/70 p-2 text-xs">
            <Badge variant="secondary">{t.toolName}</Badge>
            <p className="mt-1 break-all text-muted-foreground">参数：{t.toolInput || "无"}</p>
          </div>
        ))}
      </div>
      {block.status === "pending" && (
        <div className="mt-3 flex flex-wrap gap-2">
          <Button size="sm" className="gap-1" onClick={() => onConfirm(block, true)}>
            <Check className="h-3.5 w-3.5" /> 允许执行
          </Button>
          <Button size="sm" variant="destructive" className="gap-1" onClick={() => onConfirm(block, false)}>
            <X className="h-3.5 w-3.5" /> 拒绝
          </Button>
        </div>
      )}
    </div>
  );
});
