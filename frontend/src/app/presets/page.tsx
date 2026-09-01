"use client";

/**
 * 预设模板：卡片磁贴网格（平台/租户/个人三级作用域）。
 * 交互三态：点击磁贴 → 查看详情（Markdown 渲染）→ 编辑/新建（分区宽表单）。
 * 人格模板用 MarkdownEditor 编写，可切换实时预览。
 */
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  Code,
  Languages,
  PenLine,
  Plus,
  ScrollText,
  Search,
  Server,
  Settings2,
  ShieldAlert,
  Sparkles,
  Store,
  Trash2,
  User,
} from "lucide-react";
import AppShell from "@/components/app-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import Markdown from "@/components/markdown";
import MarkdownEditor from "@/components/markdown-editor";
import { api, isErrorCode } from "@/lib/api";
import { useAuthGuard } from "@/lib/use-auth-guard";
import { useAuthStore } from "@/store/auth";
import { ERROR_CODES } from "@/lib/types";
import type { AgentPreset } from "@/lib/types";

/** 预设图标键 → lucide 图标 */
const ICON_MAP: Record<string, React.ComponentType<{ className?: string }>> = {
  user: User,
  search: Search,
  code: Code,
  edit: PenLine,
  language: Languages,
  server: Server,
};

/** 磁贴柔和渐变底色（按索引循环，呼应「高效资产」彩色网格） */
const TILE_GRADIENTS = [
  "from-emerald-100 to-teal-50 text-emerald-600",
  "from-sky-100 to-blue-50 text-sky-600",
  "from-amber-100 to-yellow-50 text-amber-600",
  "from-teal-100 to-cyan-50 text-teal-600",
  "from-violet-100 to-purple-50 text-violet-600",
  "from-rose-100 to-pink-50 text-rose-600",
];

const SCOPE_LABEL: Record<string, string> = {
  PLATFORM: "平台",
  TENANT: "租户",
  USER: "个人",
};

function emptyPreset(): AgentPreset {
  return {
    id: 0,
    scope: "USER",
    tenantId: 0,
    ownerId: null,
    agentCode: "",
    agentName: "",
    icon: "user",
    description: "",
    sysPrompt: "",
    orderNum: 0,
    enabled: 1,
  };
}

/** 表单分区标题（图标 + 文案 + 分隔线），拉开视觉层次避免紧凑感 */
function SectionTitle({
  icon: Icon,
  title,
  desc,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  desc?: string;
}) {
  return (
    <div className="flex items-center gap-2.5 border-b pb-3">
      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-50 text-indigo-500">
        <Icon className="h-4 w-4" />
      </span>
      <div>
        <p className="text-sm font-semibold text-slate-800">{title}</p>
        {desc && <p className="text-xs text-slate-400">{desc}</p>}
      </div>
    </div>
  );
}

/** 详情元信息单元格 */
function MetaItem({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="rounded-lg bg-slate-50 px-4 py-3">
      <p className="text-xs text-slate-400">{label}</p>
      <div className="mt-1 text-sm font-medium text-slate-700">{value}</div>
    </div>
  );
}

function PresetsPage() {
  const isAdmin = useAuthStore((s) => s.isAdmin)();
  const [presets, setPresets] = useState<AgentPreset[]>([]);
  /** 查看态（点击磁贴默认进详情，再由详情进编辑） */
  const [viewing, setViewing] = useState<AgentPreset | null>(null);
  const [editing, setEditing] = useState<AgentPreset | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [saving, setSaving] = useState(false);
  /** 编码重复（5002）时高亮编码输入框 */
  const [codeError, setCodeError] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await api.get<AgentPreset[]>("/api/presets");
      setPresets(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** 平台模板仅平台管理员可改（与后端校验一致） */
  function canModify(p: AgentPreset): boolean {
    return p.scope !== "PLATFORM" || isAdmin;
  }

  /** 从详情进入编辑 */
  function startEdit(p: AgentPreset) {
    setViewing(null);
    setEditing({ ...p });
    setIsNew(false);
    setCodeError(false);
  }

  async function save() {
    if (!editing) return;
    if (!editing.agentCode || !editing.agentName || !editing.sysPrompt) {
      toast.error("编码、名称与人格内容必填");
      return;
    }
    setSaving(true);
    try {
      if (isNew) {
        await api.post("/api/presets", editing);
      } else {
        await api.put(`/api/presets/${editing.id}`, editing);
      }
      toast.success("已保存");
      setEditing(null);
      setCodeError(false);
      load();
    } catch (e) {
      // 编码重复：保持抽屉打开并高亮编码框，其余错误仅提示
      if (isErrorCode(e, ERROR_CODES.PRESET_CODE_EXISTS)) {
        setCodeError(true);
      }
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function remove(p: AgentPreset) {
    if (!window.confirm(`删除预设「${p.agentName}」？`)) return;
    try {
      await api.del(`/api/presets/${p.id}`);
      toast.success("已删除");
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  /** 发布到市场 / 取消发布 */
  async function togglePublish(p: AgentPreset) {
    try {
      if (p.published === 1) {
        await api.del(`/api/presets/${p.id}/publish`);
        toast.success("已取消发布");
      } else {
        await api.post(`/api/presets/${p.id}/publish`);
        toast.success("已发布到市场");
      }
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "操作失败");
    }
  }

  return (
    <AppShell>
      <div className="h-full overflow-auto p-6">
        <div className="space-y-4">
          {/* 页头 */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-semibold text-slate-800">人格预设</h1>
              <p className="text-sm text-slate-500">
                对话时选择预设即可切换 Agent 人格（平台 / 租户 / 个人三级）
              </p>
            </div>
            <Button
              className="gap-1 bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600"
              onClick={() => {
                setEditing(emptyPreset());
                setIsNew(true);
                setCodeError(false);
              }}
            >
              <Plus className="h-4 w-4" /> 新建预设
            </Button>
          </div>

          {/* 磁贴网格（点击进入详情） */}
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
            {presets.map((p, i) => {
              const Icon = (p.icon && ICON_MAP[p.icon]) || Sparkles;
              const gradient = TILE_GRADIENTS[i % TILE_GRADIENTS.length];
              return (
                <div
                  key={p.id}
                  className={
                    "group relative flex cursor-pointer flex-col items-center rounded-2xl border border-white/60 bg-gradient-to-br p-6 text-center shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md " +
                    gradient
                  }
                  onClick={() => setViewing({ ...p })}
                >
                  <Icon className="mb-3 h-8 w-8" />
                  <h3 className="font-medium text-slate-800">{p.agentName}</h3>
                  <p className="mt-1 line-clamp-2 min-h-8 text-xs text-slate-500">
                    {p.description || "暂无描述"}
                  </p>
                  <div className="mt-3 flex items-center gap-1.5">
                    <Badge variant="secondary" className="bg-white/70 text-xs">
                      {SCOPE_LABEL[p.scope]}
                    </Badge>
                    {p.published === 1 && (
                      <Badge variant="outline" className="border-indigo-300 text-indigo-500 text-xs">
                        <Store className="mr-0.5 h-3 w-3" /> 已发布
                      </Badge>
                    )}
                    <span className="text-xs text-slate-400">{p.agentCode}</span>
                    {p.enabled === 0 && (
                      <Badge variant="outline" className="text-xs">
                        已禁用
                      </Badge>
                    )}
                  </div>
                  {canModify(p) && (
                    <div className="absolute right-2 top-2 flex gap-1 opacity-0 group-hover:opacity-100">
                      {p.scope !== "PLATFORM" && (
                        <button
                          className="rounded-full bg-white/80 p-1.5 shadow-sm transition-colors hover:text-indigo-500"
                          title={p.published === 1 ? "取消发布" : "发布到市场"}
                          onClick={(e) => {
                            e.stopPropagation();
                            togglePublish(p);
                          }}
                        >
                          <Store className={"h-3.5 w-3.5 " + (p.published === 1 ? "text-indigo-500" : "")} />
                        </button>
                      )}
                      <button
                        className="rounded-full bg-white/80 p-1.5 shadow-sm transition-colors hover:text-rose-500"
                        title="删除"
                        onClick={(e) => {
                          e.stopPropagation();
                          remove(p);
                        }}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  )}
                </div>
              );
            })}
            {presets.length === 0 && (
              <p className="col-span-full py-10 text-center text-sm text-muted-foreground">
                暂无预设模板
              </p>
            )}
          </div>
        </div>
      </div>

      {/* 查看详情：只读 + Markdown 渲染，宽松卡片式布局 */}
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
                    <SheetTitle className="text-lg">{viewing.agentName}</SheetTitle>
                    <SheetDescription className="font-mono text-xs">
                      {viewing.agentCode}
                    </SheetDescription>
                  </div>
                </div>
                <div className="flex items-center gap-2 pt-1">
                  <Badge variant="secondary">{SCOPE_LABEL[viewing.scope]}</Badge>
                  {viewing.enabled === 0 ? (
                    <Badge variant="outline" className="text-rose-500">
                      已禁用
                    </Badge>
                  ) : (
                    <Badge variant="outline" className="text-emerald-600">
                      启用中
                    </Badge>
                  )}
                </div>
              </SheetHeader>

              <div className="flex-1 space-y-8 overflow-y-auto py-6">
                {/* 元信息 */}
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <MetaItem label="作用域" value={SCOPE_LABEL[viewing.scope]} />
                  <MetaItem label="显示顺序" value={viewing.orderNum ?? 0} />
                  <MetaItem label="归属" value={viewing.ownerId || "—"} />
                </div>

                {/* 简介 */}
                {viewing.description && (
                  <p className="rounded-lg bg-slate-50 px-4 py-3 text-sm leading-relaxed text-slate-600">
                    {viewing.description}
                  </p>
                )}

                {/* 人格模板（Markdown 渲染） */}
                <section className="space-y-3">
                  <SectionTitle
                    icon={ScrollText}
                    title="人格模板"
                    desc="对话时注入系统提示词，决定 Agent 的能力边界与风格"
                  />
                  <div className="rounded-xl border bg-slate-50/50 p-5">
                    <Markdown content={viewing.sysPrompt} />
                  </div>
                </section>
              </div>

              <SheetFooter className="border-t pt-4">
                <Button variant="outline" onClick={() => setViewing(null)}>
                  关闭
                </Button>
                {canModify(viewing) && (
                  <Button onClick={() => startEdit(viewing)}>编辑</Button>
                )}
              </SheetFooter>
            </>
          )}
        </SheetContent>
      </Sheet>

      {/* 编辑 / 新建：宽抽屉分区表单（基本信息 → 人格模板 → 状态） */}
      <Sheet open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <SheetContent className="flex w-full flex-col sm:max-w-4xl">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>{isNew ? "新建预设" : "编辑预设"}</SheetTitle>
            <SheetDescription>
              人格模板以 Markdown 编写并注入系统提示词，可切换「预览」查看渲染效果。
            </SheetDescription>
          </SheetHeader>
          {editing && (
            <div className="flex-1 space-y-8 overflow-y-auto py-6">
              {/* 基本信息 */}
              <section className="space-y-5">
                <SectionTitle icon={Settings2} title="基本信息" desc="编码在同作用域内唯一，创建后不可修改" />
                <div className="grid grid-cols-2 gap-x-6 gap-y-5">
                  <div className="space-y-2">
                    <Label>
                      编码 <span className="text-rose-500">*</span>
                    </Label>
                    <Input
                      disabled={!isNew}
                      placeholder="如 translator"
                      className={
                        "font-mono " +
                        (codeError ? "border-rose-400 ring-2 ring-rose-100" : "")
                      }
                      value={editing.agentCode}
                      onChange={(e) => {
                        setCodeError(false);
                        setEditing({ ...editing, agentCode: e.target.value });
                      }}
                    />
                    {codeError && (
                      <p className="text-xs text-rose-500">该编码在当前作用域内已存在</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label>
                      名称 <span className="text-rose-500">*</span>
                    </Label>
                    <Input
                      placeholder="如 翻译助手"
                      value={editing.agentName}
                      onChange={(e) => setEditing({ ...editing, agentName: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>作用域</Label>
                    <select
                      className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                      value={editing.scope}
                      disabled={!isNew}
                      onChange={(e) =>
                        setEditing({ ...editing, scope: e.target.value as AgentPreset["scope"] })
                      }
                    >
                      <option value="USER">个人</option>
                      <option value="TENANT">租户</option>
                      {isAdmin && <option value="PLATFORM">平台</option>}
                    </select>
                  </div>
                  <div className="space-y-2">
                    <Label>图标</Label>
                    <select
                      className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                      value={editing.icon || "user"}
                      onChange={(e) => setEditing({ ...editing, icon: e.target.value })}
                    >
                      {Object.keys(ICON_MAP).map((k) => (
                        <option key={k} value={k}>
                          {k}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="col-span-2 space-y-2">
                    <Label>简介</Label>
                    <Input
                      placeholder="一句话说明这个预设擅长什么"
                      value={editing.description || ""}
                      onChange={(e) => setEditing({ ...editing, description: e.target.value })}
                    />
                  </div>
                </div>
              </section>

              {/* 人格模板 */}
              <section className="space-y-5">
                <SectionTitle
                  icon={ScrollText}
                  title="人格模板"
                  desc="注入系统提示词，建议包含：角色定位 / 核心能力 / 风格与禁止行为"
                />
                <div className="space-y-2">
                  <Label>
                    模板内容 <span className="text-rose-500">*</span>
                  </Label>
                  <MarkdownEditor
                    minHeight={360}
                    placeholder={"# 角色定位\n你是……\n\n## 核心能力\n1. ……\n\n## 风格\n- ……"}
                    value={editing.sysPrompt}
                    onChange={(v) => setEditing({ ...editing, sysPrompt: v })}
                  />
                </div>
              </section>

              {/* 状态 */}
              <section className="space-y-5">
                <SectionTitle icon={ShieldAlert} title="启用状态" />
                <div className="flex items-center justify-between rounded-xl border px-5 py-4">
                  <div>
                    <p className="text-sm font-medium text-slate-800">启用该预设</p>
                    <p className="mt-0.5 text-xs text-slate-400">禁用后对话中不可选择</p>
                  </div>
                  <Switch
                    checked={editing.enabled === 1}
                    onCheckedChange={(checked) =>
                      setEditing({ ...editing, enabled: checked ? 1 : 0 })
                    }
                  />
                </div>
              </section>
            </div>
          )}
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setEditing(null)}>
              取消
            </Button>
            <Button onClick={save} disabled={saving} className="min-w-24">
              {saving ? "保存中..." : "保存"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </AppShell>
  );
}

export default function Page() {
  const { ready } = useAuthGuard();
  if (!ready) return null;
  return <PresetsPage />;
}
