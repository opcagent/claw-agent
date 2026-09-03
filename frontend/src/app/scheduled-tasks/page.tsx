"use client";

/**
 * 定时任务管理页面：CRUD + 启停 + 手动执行 + 日志查看。
 * 表格展示任务列表，Dialog 新建/编辑，支持 Cron 常用模板选择。
 */
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  CalendarClock,
  ChevronDown,
  Clock,
  History,
  Loader2,
  Play,
  Plus,
  Trash2,
} from "lucide-react";
import AppShell from "@/components/app-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { api } from "@/lib/api";
import { useAuthGuard } from "@/lib/use-auth-guard";
import type { AgentPipeline, AgentPreset, ScheduledTask, ScheduledTaskLog } from "@/lib/types";

/** Cron 常用模板 */
const CRON_TEMPLATES = [
  { label: "每小时", value: "0 * * * *" },
  { label: "每天 9:00", value: "0 9 * * *" },
  { label: "每天 18:00", value: "0 18 * * *" },
  { label: "工作日 9:00", value: "0 9 * * 1-5" },
  { label: "每周一 9:00", value: "0 9 * * 1" },
  { label: "每月 1 号 9:00", value: "0 9 1 * *" },
];

function emptyTask(): ScheduledTask {
  return {
    taskName: "",
    cronExpr: "0 9 * * *",
    presetCode: null,
    pipelineCode: null,
    promptContent: "",
    notifyEmail: null,
    enabled: 1,
  };
}

function ScheduledTasksPage() {
  const [tasks, setTasks] = useState<ScheduledTask[]>([]);
  const [presets, setPresets] = useState<AgentPreset[]>([]);
  const [pipelines, setPipelines] = useState<AgentPipeline[]>([]);
  const [editing, setEditing] = useState<ScheduledTask | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [saving, setSaving] = useState(false);
  const [running, setRunning] = useState<number | null>(null);
  const [logTaskId, setLogTaskId] = useState<number | null>(null);
  const [logs, setLogs] = useState<ScheduledTaskLog[]>([]);

  const load = useCallback(async () => {
    try {
      const res = await api.get<ScheduledTask[]>("/api/schedule/list");
      setTasks(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载任务列表失败");
    }
  }, []);

  useEffect(() => {
    load();
    api.get<AgentPreset[]>("/api/presets").then(r => setPresets(r.data || [])).catch(() => {});
    api.get<AgentPipeline[]>("/api/pipelines").then(r => setPipelines(r.data || [])).catch(() => {});
  }, [load]);

  /** 保存任务 */
  async function save() {
    if (!editing) return;
    if (!editing.taskName?.trim()) { toast.error("任务名称不能为空"); return; }
    if (!editing.cronExpr?.trim()) { toast.error("Cron 表达式不能为空"); return; }
    if (!editing.promptContent?.trim()) { toast.error("Prompt 内容不能为空"); return; }

    setSaving(true);
    try {
      if (isNew) {
        await api.post("/api/schedule", editing);
      } else {
        await api.put(`/api/schedule/${editing.id}`, editing);
      }
      toast.success("已保存");
      setEditing(null);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  /** 删除任务 */
  async function remove(task: ScheduledTask) {
    if (!window.confirm(`删除任务「${task.taskName}」？`)) return;
    try {
      await api.del(`/api/schedule/${task.id}`);
      toast.success("已删除");
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  /** 切换启用/禁用 */
  async function toggle(task: ScheduledTask) {
    try {
      await api.post(`/api/schedule/${task.id}/toggle`);
      toast.success(task.enabled === 1 ? "已禁用" : "已启用");
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "操作失败");
    }
  }

  /** 立即执行 */
  async function runNow(task: ScheduledTask) {
    setRunning(task.id!);
    try {
      const res = await api.post<string>(`/api/schedule/${task.id}/runNow`);
      toast.success("执行完成", { description: (res.data || "").substring(0, 100) });
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "执行失败");
    } finally {
      setRunning(null);
    }
  }

  /** 查看日志 */
  async function viewLogs(taskId: number) {
    setLogTaskId(taskId);
    try {
      const res = await api.get<ScheduledTaskLog[]>(`/api/schedule/${taskId}/logs`);
      setLogs(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载日志失败");
    }
  }

  /** 格式化时间 */
  function fmtTime(iso?: string | null): string {
    if (!iso) return "—";
    try {
      return new Date(iso).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
    } catch { return iso; }
  }

  return (
    <AppShell>
      <div className="h-full overflow-auto p-6">
        <div className="space-y-4">
          {/* 页头 */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="flex items-center gap-2 text-xl font-semibold text-slate-800">
                <CalendarClock className="h-5 w-5 text-indigo-500" />
                定时任务
              </h1>
              <p className="text-sm text-slate-500">
                按 Cron 表达式自动触发 Agent 对话，支持邮件通知
              </p>
            </div>
            <Button
              className="gap-1 bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600"
              onClick={() => { setEditing(emptyTask()); setIsNew(true); }}
            >
              <Plus className="h-4 w-4" /> 新建任务
            </Button>
          </div>

          {/* 任务表格 */}
          <div className="rounded-2xl border bg-white">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-slate-50/80 text-left text-xs text-muted-foreground">
                  <th className="px-4 py-3 font-medium">任务名称</th>
                  <th className="px-4 py-3 font-medium">Cron</th>
                  <th className="px-4 py-3 font-medium">预设</th>
                  <th className="px-4 py-3 font-medium">状态</th>
                  <th className="px-4 py-3 font-medium">上次执行</th>
                  <th className="px-4 py-3 font-medium">下次执行</th>
                  <th className="px-4 py-3 font-medium text-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {tasks.map((t) => (
                  <tr key={t.id} className="border-b last:border-0 hover:bg-slate-50/50">
                    <td className="px-4 py-3 font-medium text-slate-700">{t.taskName}</td>
                    <td className="px-4 py-3">
                      <code className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
                        {t.cronExpr}
                      </code>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {t.presetCode || "—"}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <Switch
                          checked={t.enabled === 1}
                          onCheckedChange={() => toggle(t)}
                        />
                        <Badge
                          variant="secondary"
                          className={t.enabled === 1 ? "bg-emerald-50 text-emerald-600" : "bg-slate-100 text-slate-500"}
                        >
                          {t.enabled === 1 ? "启用" : "禁用"}
                        </Badge>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{fmtTime(t.lastRunTime)}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">{fmtTime(t.nextRunTime)}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          title="立即执行"
                          disabled={running === t.id}
                          onClick={() => runNow(t)}
                        >
                          {running === t.id ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <Play className="h-3.5 w-3.5" />
                          )}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          title="执行日志"
                          onClick={() => viewLogs(t.id!)}
                        >
                          <History className="h-3.5 w-3.5" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          title="编辑"
                          onClick={() => { setEditing({ ...t }); setIsNew(false); }}
                        >
                          编辑
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          title="删除"
                          onClick={() => remove(t)}
                          className="text-rose-500 hover:text-rose-600"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
                {tasks.length === 0 && (
                  <tr>
                    <td colSpan={7} className="px-4 py-10 text-center text-sm text-muted-foreground">
                      暂无定时任务
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* 新建/编辑 Dialog */}
      <Dialog open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{isNew ? "新建定时任务" : "编辑定时任务"}</DialogTitle>
            <DialogDescription>
              配置 Cron 表达式和 Prompt，系统将自动触发 Agent 对话
            </DialogDescription>
          </DialogHeader>
          {editing && (
            <div className="space-y-4 py-2">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>任务名称 *</Label>
                  <Input
                    placeholder="如：每日早报"
                    value={editing.taskName}
                    onChange={(e) => setEditing({ ...editing, taskName: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label>Cron 表达式 *</Label>
                  <div className="flex gap-2">
                    <Input
                      className="font-mono"
                      placeholder="0 9 * * *"
                      value={editing.cronExpr}
                      onChange={(e) => setEditing({ ...editing, cronExpr: e.target.value })}
                    />
                    <select
                      className="h-9 rounded-md border bg-background px-2 text-xs"
                      onChange={(e) => { if (e.target.value) setEditing({ ...editing, cronExpr: e.target.value }); }}
                      value=""
                    >
                      <option value="">常用模板</option>
                      {CRON_TEMPLATES.map((t) => (
                        <option key={t.value} value={t.value}>{t.label}</option>
                      ))}
                    </select>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>预设模板</Label>
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                    value={editing.presetCode || ""}
                    onChange={(e) => setEditing({ ...editing, presetCode: e.target.value || null })}
                  >
                    <option value="">默认人格</option>
                    {presets.filter(p => p.enabled === 1).map(p => (
                      <option key={p.id} value={p.agentCode}>{p.agentName}</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2">
                  <Label>流水线</Label>
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                    value={editing.pipelineCode || ""}
                    onChange={(e) => setEditing({ ...editing, pipelineCode: e.target.value || null })}
                  >
                    <option value="">无流水线</option>
                    {pipelines.map(p => (
                      <option key={p.id} value={p.pipelineCode}>{p.pipelineName}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="space-y-2">
                <Label>Prompt 内容 *</Label>
                <Textarea
                  className="min-h-24"
                  placeholder="发送给 Agent 的消息内容…"
                  value={editing.promptContent}
                  onChange={(e) => setEditing({ ...editing, promptContent: e.target.value })}
                />
              </div>

              <div className="space-y-2">
                <Label>通知邮箱（可选）</Label>
                <Input
                  type="email"
                  placeholder="执行结果发送到此邮箱"
                  value={editing.notifyEmail || ""}
                  onChange={(e) => setEditing({ ...editing, notifyEmail: e.target.value || null })}
                />
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>取消</Button>
            <Button onClick={save} disabled={saving} className="min-w-24">
              {saving ? "保存中…" : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 执行日志 Sheet */}
      <Sheet open={!!logTaskId} onOpenChange={(open) => !open && setLogTaskId(null)}>
        <SheetContent className="flex w-full flex-col sm:max-w-2xl">
          <SheetHeader>
            <SheetTitle className="flex items-center gap-2">
              <Clock className="h-4 w-4" />
              执行日志
            </SheetTitle>
            <SheetDescription>最近 50 条执行记录</SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-2 overflow-y-auto py-4">
            {logs.length > 0 ? logs.map((log) => (
              <div
                key={log.id}
                className="rounded-lg border p-3 text-sm"
              >
                <div className="flex items-center justify-between">
                  <Badge
                    variant={log.status === "SUCCESS" ? "default" : "destructive"}
                    className={log.status === "SUCCESS" ? "bg-emerald-500" : ""}
                  >
                    {log.status === "SUCCESS" ? "成功" : "失败"}
                  </Badge>
                  <span className="text-xs text-muted-foreground">
                    {fmtTime(log.runTime)}
                  </span>
                </div>
                {log.resultText && (
                  <p className="mt-2 line-clamp-3 text-xs text-muted-foreground">
                    {log.resultText}
                  </p>
                )}
                {log.errorMsg && (
                  <p className="mt-1 text-xs text-rose-500">{log.errorMsg}</p>
                )}
              </div>
            )) : (
              <p className="py-10 text-center text-sm text-muted-foreground">
                暂无执行记录
              </p>
            )}
          </div>
        </SheetContent>
      </Sheet>
    </AppShell>
  );
}

export default function Page() {
  const { ready } = useAuthGuard();
  if (!ready) return null;
  return <ScheduledTasksPage />;
}
