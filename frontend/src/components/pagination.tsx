"use client";

/**
 * 分页器：总数 + 上一页/下一页 + 页码按钮。
 * 页码多时省略中段（保留首页 / 末页 / 当前页邻域，省略处以省略号表示）。
 */
import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";

interface PaginationProps {
  /** 当前页码（从 1 起） */
  page: number;
  /** 每页条数 */
  pageSize: number;
  /** 总记录数 */
  total: number;
  /** 翻页回调 */
  onChange: (page: number) => void;
}

/** 生成待展示的页码序列（null 代表省略号） */
function buildPages(page: number, totalPages: number): (number | null)[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i + 1);
  }
  const keep = new Set<number>([1, totalPages, page - 1, page, page + 1]);
  const sorted = Array.from(keep)
    .filter((p) => p >= 1 && p <= totalPages)
    .sort((a, b) => a - b);
  const out: (number | null)[] = [];
  let prev = 0;
  for (const p of sorted) {
    if (prev && p - prev > 1) out.push(null);
    out.push(p);
    prev = p;
  }
  return out;
}

export default function Pagination({ page, pageSize, total, onChange }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  if (total === 0) return null;

  const btnBase =
    "flex h-7 min-w-7 items-center justify-center rounded-md px-1.5 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-40";

  return (
    <div className="flex items-center justify-between px-4 py-3">
      <span className="text-xs text-muted-foreground">
        共 {total} 条 · 第 {page}/{totalPages} 页
      </span>
      <div className="flex items-center gap-1">
        <button
          className={cn(btnBase, "border text-slate-600 hover:bg-slate-100")}
          disabled={page <= 1}
          onClick={() => onChange(page - 1)}
          aria-label="上一页"
        >
          <ChevronLeft className="h-3.5 w-3.5" />
        </button>
        {buildPages(page, totalPages).map((p, i) =>
          p === null ? (
            <span key={`e-${i}`} className="px-1 text-xs text-slate-400">
              …
            </span>
          ) : (
            <button
              key={p}
              className={cn(
                btnBase,
                p === page
                  ? "bg-indigo-500 font-medium text-white"
                  : "border text-slate-600 hover:bg-slate-100"
              )}
              onClick={() => onChange(p)}
            >
              {p}
            </button>
          )
        )}
        <button
          className={cn(btnBase, "border text-slate-600 hover:bg-slate-100")}
          disabled={page >= totalPages}
          onClick={() => onChange(page + 1)}
          aria-label="下一页"
        >
          <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}
