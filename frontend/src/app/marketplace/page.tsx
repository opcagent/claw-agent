"use client";

/**
 * 预设模板市场：浏览 + 使用已发布的预设模板。
 * 卡片网格展示，支持搜索 + 按使用次数排序。
 * 点击「使用」→ 复制到个人模板 → 跳转预设页。
 */
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  ArrowRight,
  Download,
  Search,
  Sparkles,
  User,
  Code,
  PenLine,
  Languages,
  Server,
  Store,
} from "lucide-react";
import AppShell from "@/components/app-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import Markdown from "@/components/markdown";
import { api } from "@/lib/api";
import { useAuthGuard } from "@/lib/use-auth-guard";
import type { MarketplacePreset } from "@/lib/types";

/** 预设图标键 → lucide 图标 */
const ICON_MAP: Record<string, React.ComponentType<{ className?: string }>> = {
  user: User,
  search: Search,
  code: Code,
  edit: PenLine,
  language: Languages,
  server: Server,
};

/** 磁贴柔和渐变底色 */
const TILE_GRADIENTS = [
  "from-emerald-100 to-teal-50 text-emerald-600",
  "from-sky-100 to-blue-50 text-sky-600",
  "from-amber-100 to-yellow-50 text-amber-600",
  "from-teal-100 to-cyan-50 text-teal-600",
  "from-violet-100 to-purple-50 text-violet-600",
  "from-rose-100 to-pink-50 text-rose-600",
];

function MarketplacePage() {
  const [presets, setPresets] = useState<MarketplacePreset[]>([]);
  const [search, setSearch] = useState("");
  const [viewing, setViewing] = useState<MarketplacePreset | null>(null);
  const [using, setUsing] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await api.get<MarketplacePreset[]>("/api/presets/marketplace");
      setPresets(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载市场模板失败");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** 从市场使用模板：复制到个人作用域 */
  async function usePreset(p: MarketplacePreset) {
    setUsing(true);
    try {
      await api.post(`/api/presets/marketplace/${p.id}/use`);
      toast.success(`已复制「${p.publishName || p.agentName}」到个人模板`);
      setViewing(null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "使用失败");
    } finally {
      setUsing(false);
    }
  }

  /** 按搜索词过滤 */
  const filtered = presets.filter(
    (p) =>
      !search ||
      (p.publishName || p.agentName).toLowerCase().includes(search.toLowerCase()) ||
      (p.publishDesc || p.description || "").toLowerCase().includes(search.toLowerCase())
  );

  return (
    <AppShell>
      <div className="h-full overflow-auto p-6">
        <div className="space-y-4">
          {/* 页头 */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="flex items-center gap-2 text-xl font-semibold text-slate-800">
                <Store className="h-5 w-5 text-indigo-500" />
                模板市场
              </h1>
              <p className="text-sm text-slate-500">
                浏览其他用户发布的预设模板，一键复制到个人模板使用
              </p>
            </div>
          </div>

          {/* 搜索栏 */}
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-9"
              placeholder="搜索模板名称或描述…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          {/* 卡片网格 */}
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
            {filtered.map((p, i) => {
              const Icon = (p.icon && ICON_MAP[p.icon]) || Sparkles;
              const gradient = TILE_GRADIENTS[i % TILE_GRADIENTS.length];
              return (
                <div
                  key={p.id}
                  className={
                    "group relative flex cursor-pointer flex-col items-center rounded-2xl border border-white/60 bg-gradient-to-br p-6 text-center shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md " +
                    gradient
                  }
                  onClick={() => setViewing(p)}
                >
                  <Icon className="mb-3 h-8 w-8" />
                  <h3 className="font-medium text-slate-800">
                    {p.publishName || p.agentName}
                  </h3>
                  <p className="mt-1 line-clamp-2 min-h-8 text-xs text-slate-500">
                    {p.publishDesc || p.description || "暂无描述"}
                  </p>
                  <div className="mt-3 flex items-center gap-2">
                    <Badge variant="secondary" className="bg-white/70 text-xs">
                      <Download className="mr-1 h-3 w-3" />
                      {p.useCount || 0}
                    </Badge>
                    <span className="text-xs text-slate-400">
                      by {p.authorName || p.ownerId || "匿名"}
                    </span>
                  </div>
                </div>
              );
            })}
            {filtered.length === 0 && (
              <p className="col-span-full py-10 text-center text-sm text-muted-foreground">
                {search ? "未找到匹配的模板" : "市场暂无模板，快去发布第一个吧"}
              </p>
            )}
          </div>
        </div>
      </div>

      {/* 查看详情 */}
      <Sheet open={!!viewing} onOpenChange={(open) => !open && setViewing(null)}>
        <SheetContent className="flex w-full flex-col sm:max-w-3xl">
          {viewing && (
            <>
              <SheetHeader className="border-b pb-5">
                <div className="flex items-center gap-3">
                  {(() => {
                    const Icon = (viewing.icon && ICON_MAP[viewing.icon]) || Sparkles;
                    return (
                      <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-500 text-white shadow-sm">
                        <Icon className="h-5 w-5" />
                      </span>
                    );
                  })()}
                  <div>
                    <SheetTitle className="text-lg">
                      {viewing.publishName || viewing.agentName}
                    </SheetTitle>
                    <SheetDescription>
                      作者：{viewing.authorName || viewing.ownerId || "匿名"} · 使用 {viewing.useCount || 0} 次
                    </SheetDescription>
                  </div>
                </div>
              </SheetHeader>

              <div className="flex-1 space-y-6 overflow-y-auto py-6">
                {/* 发布描述 */}
                {viewing.publishDesc && (
                  <p className="rounded-lg bg-slate-50 px-4 py-3 text-sm leading-relaxed text-slate-600">
                    {viewing.publishDesc}
                  </p>
                )}

                {/* 系统提示词预览 */}
                <div className="space-y-2">
                  <p className="text-sm font-medium text-slate-700">系统提示词预览</p>
                  <div className="rounded-xl border bg-slate-50/50 p-5">
                    <Markdown content={viewing.sysPrompt} />
                  </div>
                </div>
              </div>

              <SheetFooter className="border-t pt-4">
                <Button variant="outline" onClick={() => setViewing(null)}>
                  关闭
                </Button>
                <Button
                  onClick={() => usePreset(viewing)}
                  disabled={using}
                  className="gap-1 bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600"
                >
                  {using ? (
                    "复制中…"
                  ) : (
                    <>
                      <ArrowRight className="h-4 w-4" /> 使用此模板
                    </>
                  )}
                </Button>
              </SheetFooter>
            </>
          )}
        </SheetContent>
      </Sheet>
    </AppShell>
  );
}

export default function Page() {
  const { ready } = useAuthGuard();
  if (!ready) return null;
  return <MarketplacePage />;
}
