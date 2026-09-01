"use client";

/**
 * 流水线编排：卡片磁贴网格（平台/租户/个人三级作用域）。
 * 交互三态：点击磁贴 → 查看详情（Markdown 渲染）→ 编辑/新建（分区宽表单）。
 * 执行步骤与异常处理策略用 MarkdownEditor 编写，可切换实时预览。
 */
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { ListOrdered, Plus, Settings2, ShieldAlert, Trash2, Workflow } from "lucide-react";
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
import type { AgentPipeline } from "@/lib/types";

/** 磁贴柔和渐变底色（按索引循环，与预设页视觉一致） */
const TILE_GRADIENTS = [
  "from-sky-100 to-blue-50 text-sky-600",
  "from-violet-100 to-purple-50 text-violet-600",
  "from-emerald-100 to-teal-50 text-emerald-600",
  "from-amber-100 to-yellow-50 text-amber-600",
  "from-rose-100 to-pink-50 text-rose-600",
  "from-teal-100 to-cyan-50 text-teal-600",
];

const SCOPE_LABEL: Record<string, string> = {
  PLATFORM: "平台",
  TENANT: "租户",
  USER: "个人",
};

function emptyPipeline(): AgentPipeline {
  return {
    scope: "USER",
    pipelineCode: "",
    pipelineName: "",
    description: "",
    steps: "",
    exceptionHandling: "",
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

function PipelinesPage() {
  const isAdmin = useAuthStore((s) => s.isAdmin)();
  const [pipelines, setPipelines] = useState<AgentPipeline[]>([]);
  /** 查看态（点击磁贴默认进详情，再由详情进编辑） */
  const [viewing, setViewing] = useState<AgentPipeline | null>(null);
  const [editing, setEditing] = useState<AgentPipeline | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [saving, setSaving] = useState(false);
  /** 编码重复（4002）时高亮编码输入框 */
  const [codeError, setCodeError] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await api.get<AgentPipeline[]>("/api/pipelines");
      setPipelines(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** 平台流水线仅平台管理员可改（与后端校验一致） */
  function canModify(p: AgentPipeline): boolean {
    return p.scope !== "PLATFORM" || isAdmin;
  }

  /** 从详情进入编辑 */
  function startEdit(p: AgentPipeline) {
    setViewing(null);
    setEditing({ ...p });
    setIsNew(false);
    setCodeError(false);
  }

  async function save() {
    if (!editing) return;
    if (!editing.pipelineCode || !editing.pipelineName || !editing.steps) {
      toast.error("编码、名称与执行步骤必填");
      return;
    }
    setSaving(true);
    try {
      if (isNew) {
        await api.post("/api/pipelines", editing);
      } else {
        await api.put(`/api/pipelines/${editing.id}`, editing);
      }
      toast.success("已保存");
      setEditing(null);
      setCodeError(false);
      load();
    } catch (e) {
      // 编码重复：保持抽屉打开并高亮编码框，其余错误仅提示
      if (isErrorCode(e, ERROR_CODES.PIPELINE_CODE_EXISTS)) {
        setCodeError(true);
      }
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function remove(p: AgentPipeline) {
    if (!window.confirm(`删除流水线「${p.pipelineName}」？`)) return;
    try {
      await api.del(`/api/pipelines/${p.id}`);
      toast.success("已删除");
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  return (
    <AppShell>
      <div className="h-full overflow-auto p-6">
        <div className="space-y-4">
          {/* 页头 */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-semibold text-slate-800">自动化流水线</h1>
              <p className="text-sm text-slate-500">
                把多步骤任务固化为可复用剧本，对话时选择流水线即可按步骤执行（平台 / 租户 / 个人三级）
              </p>
            </div>
            <Button
              className="gap-1 bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600"
              onClick={() => {
                setEditing(emptyPipeline());
                setIsNew(true);
                setCodeError(false);
              }}
            >
              <Plus className="h-4 w-4" /> 新建流水线
            </Button>
          </div>

          {/* 磁贴网格（点击进入详情） */}
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
            {pipelines.map((p, i) => {
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
                  <Workflow className="mb-3 h-8 w-8" />
                  <h3 className="font-medium text-slate-800">{p.pipelineName}</h3>
                  <p className="mt-1 line-clamp-2 min-h-8 text-xs text-slate-500">
                    {p.description || "暂无描述"}
                  </p>
                  <div className="mt-3 flex items-center gap-1.5">
                    <Badge variant="secondary" className="bg-white/70 text-xs">
                      {SCOPE_LABEL[p.scope]}
                    </Badge>
                    <span className="text-xs text-slate-400">{p.pipelineCode}</span>
                    {p.enabled === 0 && (
                      <Badge variant="outline" className="text-xs">
                        已禁用
                      </Badge>
                    )}
                  </div>
                  {canModify(p) && (
                    <button
                      className="absolute right-2 top-2 rounded-full bg-white/80 p-1.5 opacity-0 shadow-sm transition-opacity hover:text-rose-500 group-hover:opacity-100"
                      title="删除"
                      onClick={(e) => {
                        e.stopPropagation();
                        remove(p);
                      }}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
              );
            })}
            {pipelines.length === 0 && (
              <p className="col-span-full py-10 text-center text-sm text-muted-foreground">
                暂无流水线
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
                  <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-500 text-white shadow-sm">
                    <Workflow className="h-5 w-5" />
                  </span>
                  <div>
                    <SheetTitle className="text-lg">{viewing.pipelineName}</SheetTitle>
                    <SheetDescription className="font-mono text-xs">
                      {viewing.pipelineCode}
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

                {/* 执行步骤（Markdown 渲染） */}
                <section className="space-y-3">
                  <SectionTitle
                    icon={ListOrdered}
                    title="执行步骤"
                    desc="对话时随用户消息注入，按剧本逐步执行"
                  />
                  <div className="rounded-xl border bg-slate-50/50 p-5">
                    <Markdown content={viewing.steps} />
                  </div>
                </section>

                {/* 异常处理策略 */}
                {viewing.exceptionHandling && (
                  <section className="space-y-3">
                    <SectionTitle icon={ShieldAlert} title="异常处理策略" desc="步骤失败时的兜底行为" />
                    <div className="rounded-xl border bg-amber-50/40 p-5">
                      <Markdown content={viewing.exceptionHandling} />
                    </div>
                  </section>
                )}
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

      {/* 编辑 / 新建：宽抽屉分区表单（基本信息 → 执行剧本 → 状态） */}
      <Sheet open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <SheetContent className="flex w-full flex-col sm:max-w-4xl">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>{isNew ? "新建流水线" : "编辑流水线"}</SheetTitle>
            <SheetDescription>
              执行步骤以 Markdown 编写（Step N + 动作 + 输出），可切换「预览」查看渲染效果。
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
                      placeholder="如 weekly-report"
                      className={
                        "font-mono " +
                        (codeError ? "border-rose-400 ring-2 ring-rose-100" : "")
                      }
                      value={editing.pipelineCode}
                      onChange={(e) => {
                        setCodeError(false);
                        setEditing({ ...editing, pipelineCode: e.target.value });
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
                      placeholder="如 周报生成流水线"
                      value={editing.pipelineName}
                      onChange={(e) => setEditing({ ...editing, pipelineName: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>作用域</Label>
                    <select
                      className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                      value={editing.scope}
                      disabled={!isNew}
                      onChange={(e) =>
                        setEditing({ ...editing, scope: e.target.value as AgentPipeline["scope"] })
                      }
                    >
                      <option value="USER">个人</option>
                      <option value="TENANT">租户</option>
                      {isAdmin && <option value="PLATFORM">平台</option>}
                    </select>
                  </div>
                  <div className="space-y-2">
                    <Label>显示顺序</Label>
                    <Input
                      type="number"
                      value={editing.orderNum ?? 0}
                      onChange={(e) => setEditing({ ...editing, orderNum: Number(e.target.value) })}
                    />
                  </div>
                  <div className="col-span-2 space-y-2">
                    <Label>简介</Label>
                    <Input
                      placeholder="一句话说明这条流水线做什么"
                      value={editing.description || ""}
                      onChange={(e) => setEditing({ ...editing, description: e.target.value })}
                    />
                  </div>
                </div>
              </section>

              {/* 执行剧本 */}
              <section className="space-y-5">
                <SectionTitle
                  icon={ListOrdered}
                  title="执行剧本"
                  desc="对话时随消息注入当轮上下文，由主 Agent 按步骤执行"
                />
                <div className="space-y-2">
                  <Label>
                    执行步骤 <span className="text-rose-500">*</span>
                  </Label>
                  <MarkdownEditor
                    minHeight={320}
                    placeholder={"Step 1: 收集需求信息，输出要点清单\nStep 2: 基于要点撰写初稿\nStep 3: 自查并输出终稿"}
                    value={editing.steps}
                    onChange={(v) => setEditing({ ...editing, steps: v })}
                  />
                </div>
                <div className="space-y-2">
                  <Label>异常处理策略（可选）</Label>
                  <MarkdownEditor
                    minHeight={140}
                    placeholder="如：任一步骤失败时停止执行，汇总已完成部分与失败原因"
                    value={editing.exceptionHandling || ""}
                    onChange={(v) => setEditing({ ...editing, exceptionHandling: v })}
                  />
                </div>
              </section>

              {/* 状态 */}
              <section className="space-y-5">
                <SectionTitle icon={ShieldAlert} title="启用状态" />
                <div className="flex items-center justify-between rounded-xl border px-5 py-4">
                  <div>
                    <p className="text-sm font-medium text-slate-800">启用该流水线</p>
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
  return <PipelinesPage />;
}
