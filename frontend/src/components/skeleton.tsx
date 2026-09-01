"use client";

import { cn } from "@/lib/utils";

/**
 * 骨架屏组件 - 用于加载状态占位
 */
interface SkeletonProps {
  className?: string;
  variant?: "text" | "circular" | "rectangular";
}

export function Skeleton({ className, variant = "text" }: SkeletonProps) {
  const baseClasses = "animate-pulse bg-muted";
  
  const variantClasses = {
    text: "h-4 rounded",
    circular: "rounded-full",
    rectangular: "rounded-md",
  };

  return (
    <div className={cn(baseClasses, variantClasses[variant], className)} />
  );
}

/**
 * 消息气泡骨架屏
 */
export function MessageSkeleton() {
  return (
    <div className="flex gap-2 animate-pulse">
      {/* 头像占位 */}
      <Skeleton variant="circular" className="h-8 w-8" />
      
      {/* 内容占位 */}
      <div className="flex-1 space-y-2 max-w-[90%] lg:max-w-[80%]">
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
      </div>
    </div>
  );
}

/**
 * 会话列表骨架屏
 */
export function SessionListSkeleton() {
  return (
    <div className="space-y-2 p-2">
      {[1, 2, 3, 4, 5].map((i) => (
        <div key={i} className="flex items-center gap-3 p-3 rounded-lg bg-muted/30">
          <Skeleton variant="circular" className="h-8 w-8" />
          <div className="flex-1 space-y-1">
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-3 w-1/2" />
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * 预设列表骨架屏
 */
export function PresetListSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-2 p-2">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="p-3 rounded-lg border bg-muted/20">
          <Skeleton className="h-4 w-2/3 mb-2" />
          <Skeleton className="h-3 w-full" />
        </div>
      ))}
    </div>
  );
}
