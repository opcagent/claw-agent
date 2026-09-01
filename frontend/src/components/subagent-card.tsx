"use client";

import React, { useState } from "react";
import { ChevronDown, ChevronUp, Clock, Loader2, Check, X, Bot } from "lucide-react";
import { Badge } from "@/components/ui/badge";

export interface SubagentToolCall {
  name: string;
  status: "running" | "success" | "error";
  duration?: number; // 秒
}

export interface SubagentCardProps {
  subagentId: string;
  label: string;
  status: "running" | "completed" | "failed";
  startTime: string;
  endTime?: string;
  toolCalls?: SubagentToolCall[];
  outputPreview?: string;
}

/**
 * 子 Agent 卡片组件 - 可展开显示详细信息
 * 
 * 折叠状态: 显示简要信息 (名称 + 状态 + 耗时)
 * 展开状态: 显示工具调用列表、输出预览等详情
 */
export function SubagentCard({
  subagentId,
  label,
  status,
  startTime,
  endTime,
  toolCalls = [],
  outputPreview,
}: SubagentCardProps) {
  const [expanded, setExpanded] = useState(false);

  // 计算耗时
  const duration = React.useMemo(() => {
    const start = new Date(startTime).getTime();
    const end = endTime ? new Date(endTime).getTime() : Date.now();
    return ((end - start) / 1000).toFixed(1);
  }, [startTime, endTime]);

  // 获取状态图标和颜色
  const getStatusInfo = () => {
    switch (status) {
      case "running":
        return {
          icon: <Loader2 className="h-4 w-4 animate-spin text-blue-500" />,
          badge: <Badge variant="secondary" className="bg-blue-100 text-blue-700">运行中</Badge>,
          borderColor: "border-blue-200",
          bgColor: "bg-blue-50/50",
        };
      case "completed":
        return {
          icon: <Check className="h-4 w-4 text-emerald-500" />,
          badge: <Badge variant="secondary" className="bg-emerald-100 text-emerald-700">已完成</Badge>,
          borderColor: "border-emerald-200",
          bgColor: "bg-emerald-50/50",
        };
      case "failed":
        return {
          icon: <X className="h-4 w-4 text-rose-500" />,
          badge: <Badge variant="destructive">失败</Badge>,
          borderColor: "border-rose-200",
          bgColor: "bg-rose-50/50",
        };
    }
  };

  const statusInfo = getStatusInfo();

  // 根据子 Agent 类型获取专属图标和颜色
  const getSubagentType = () => {
    if (label.includes("researcher") || label.includes("研究")) {
      return {
        icon: "🔍",
        color: "text-blue-600",
        gradient: "from-blue-500 to-cyan-500",
      };
    }
    if (label.includes("analyst") || label.includes("分析")) {
      return {
        icon: "📊",
        color: "text-purple-600",
        gradient: "from-purple-500 to-pink-500",
      };
    }
    if (label.includes("writer") || label.includes("写作")) {
      return {
        icon: "✍️",
        color: "text-orange-600",
        gradient: "from-orange-500 to-yellow-500",
      };
    }
    return {
      icon: "🤖",
      color: "text-indigo-600",
      gradient: "from-indigo-500 to-violet-500",
    };
  };

  const typeInfo = getSubagentType();

  return (
    <div
      className={`my-3 rounded-lg border ${statusInfo.borderColor} ${statusInfo.bgColor} shadow-sm transition-all hover:shadow-md`}
    >
      {/* 头部 - 始终可见 */}
      <div
        className="flex items-center gap-3 px-4 py-3 cursor-pointer select-none"
        onClick={() => setExpanded(!expanded)}
      >
        {/* 左侧: 图标 + 名称 */}
        <div className="flex items-center gap-2 flex-1">
          <span className="text-xl">{typeInfo.icon}</span>
          <div>
            <p className={`text-sm font-medium ${typeInfo.color}`}>
              {label}
            </p>
            <p className="text-xs text-muted-foreground font-mono">
              ID: {subagentId.slice(0, 8)}...
            </p>
          </div>
        </div>

        {/* 中间: 状态徽章 */}
        <div className="flex items-center gap-2">
          {statusInfo.icon}
          {statusInfo.badge}
        </div>

        {/* 右侧: 耗时 + 展开按钮 */}
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1 text-xs text-muted-foreground">
            <Clock className="h-3.5 w-3.5" />
            <span>{duration}s</span>
          </div>
          <button
            className="p-1 hover:bg-background rounded transition-colors"
            aria-label={expanded ? "收起" : "展开"}
          >
            {expanded ? (
              <ChevronUp className="h-4 w-4 text-muted-foreground" />
            ) : (
              <ChevronDown className="h-4 w-4 text-muted-foreground" />
            )}
          </button>
        </div>
      </div>

      {/* 展开内容 - 仅当 expanded=true 时显示 */}
      {expanded && (
        <div className="px-4 pb-4 space-y-4 border-t border-border/50 pt-3">
          {/* 工具调用列表 */}
          {toolCalls.length > 0 && (
            <div>
              <h4 className="text-xs font-medium text-muted-foreground mb-2">
                执行的工具 ({toolCalls.length})
              </h4>
              <div className="space-y-1.5">
                {toolCalls.map((tool, idx) => (
                  <div
                    key={idx}
                    className="flex items-center gap-2 text-xs bg-background/50 rounded px-2 py-1.5"
                  >
                    {tool.status === "running" && (
                      <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-500" />
                    )}
                    {tool.status === "success" && (
                      <Check className="h-3.5 w-3.5 text-emerald-500" />
                    )}
                    {tool.status === "error" && (
                      <X className="h-3.5 w-3.5 text-rose-500" />
                    )}
                    <span className="flex-1 font-mono text-foreground/80">
                      {tool.name}
                    </span>
                    {tool.duration && (
                      <span className="text-muted-foreground text-[10px]">
                        {tool.duration.toFixed(1)}s
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 输出预览 */}
          {outputPreview && (
            <div>
              <h4 className="text-xs font-medium text-muted-foreground mb-2">
                输出预览
              </h4>
              <div className="bg-background/50 rounded p-3 text-xs font-mono max-h-40 overflow-y-auto whitespace-pre-wrap break-all border border-border/50">
                {outputPreview.length > 500
                  ? `${outputPreview.slice(0, 500)}...`
                  : outputPreview}
              </div>
            </div>
          )}

          {/* 时间线 */}
          <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
            <Clock className="h-3 w-3" />
            <span>开始: {new Date(startTime).toLocaleTimeString()}</span>
            {endTime && (
              <>
                <span>→</span>
                <span>结束: {new Date(endTime).toLocaleTimeString()}</span>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * 简化的子 Agent 行组件 - 用于紧凑展示
 * 适合在消息气泡内嵌入
 */
export function SubagentInline({
  subagentId,
  label,
  status,
}: {
  subagentId: string;
  label: string;
  status: "running" | "completed" | "failed";
}) {
  const getStatusIcon = () => {
    switch (status) {
      case "running":
        return <Loader2 className="h-3.5 w-3.5 animate-spin text-blue-500" />;
      case "completed":
        return <Check className="h-3.5 w-3.5 text-emerald-500" />;
      case "failed":
        return <X className="h-3.5 w-3.5 text-rose-500" />;
    }
  };

  return (
    <div className="flex items-center gap-2 text-xs bg-muted/30 rounded px-2 py-1 inline-flex">
      {getStatusIcon()}
      <span className="font-medium">{label}</span>
      <span className="text-muted-foreground font-mono text-[10px]">
        ({subagentId.slice(0, 6)})
      </span>
    </div>
  );
}
