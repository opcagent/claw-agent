"use client";

/**
 * 在线监控：保留窗口（30 分钟）内活跃用户列表，只读。
 * 在线 = 最近 5 分钟内有认证通过的请求（JWT 无状态下的活跃近似，后端判定下发）。
 * 权限：菜单隐藏 + 页面守卫（租户管理员及以上）+ 后端 /api/admin/** 角色校验三重防护。
 * 支持手动刷新与 30 秒自动刷新（开关默认开启）。
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { Activity, Monitor, RefreshCw, Users } from "lucide-react";
import { toast } from "sonner";
import AppShell from "@/components/app-shell";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { api } from "@/lib/api";
import { useAuthGuard } from "@/lib/use-auth-guard";
import type { OnlineUser } from "@/lib/types";

/** 自动刷新间隔（毫秒），与在线判定阈值（5 分钟）相比足够实时又不至于压垮后端 */
const AUTO_REFRESH_MS = 30_000;

/** 时间展示：后端已统一下发「yyyy-MM-dd HH:mm:ss」，空值显示横杠 */
function fmtTime(t?: string | null): string {
  return t || "-";
}

function OnlineMonitorPage() {
  const [users, setUsers] = useState<OnlineUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  // 自动刷新定时器引用：开关切换与卸载时清理，避免内存泄漏与后台轮询残留
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<OnlineUser[]>("/api/adminOnline/list");
      setUsers(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // 自动刷新：开关打开期间按固定间隔轮询；关闭即停
  useEffect(() => {
    if (!autoRefresh) return;
    timerRef.current = setInterval(load, AUTO_REFRESH_MS);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [autoRefresh, load]);

  const onlineCount = users.filter((u) => u.online).length;

  return (
    <AppShell>
      <div className="h-full overflow-auto p-6">
        <div className="space-y-4">
          {/* 页头：说明口径 + 刷新控制 */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-semibold text-slate-800">在线监控</h1>
              <p className="text-sm text-slate-500">
                在线 = 最近 5 分钟有操作；列表仅保留 30 分钟内有活动的用户
              </p>
            </div>
            <div className="flex items-center gap-2">
              <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-600">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded accent-indigo-500"
                  checked={autoRefresh}
                  onChange={(e) => setAutoRefresh(e.target.checked)}
                />
                自动刷新（30s）
              </label>
              <Button variant="outline" size="sm" className="gap-1" onClick={load} disabled={loading}>
                <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
                刷新
              </Button>
            </div>
          </div>

          {/* 统计卡：在线人数 / 活跃人数 */}
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex items-center gap-4 rounded-2xl border border-slate-200/70 bg-white p-5 shadow-sm">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-400 to-teal-500 text-white shadow-sm">
                <Activity className="h-5 w-5" />
              </span>
              <div>
                <p className="text-2xl font-semibold text-slate-800">{onlineCount}</p>
                <p className="text-xs text-muted-foreground">当前在线</p>
              </div>
            </div>
            <div className="flex items-center gap-4 rounded-2xl border border-slate-200/70 bg-white p-5 shadow-sm">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-400 to-violet-500 text-white shadow-sm">
                <Users className="h-5 w-5" />
              </span>
              <div>
                <p className="text-2xl font-semibold text-slate-800">{users.length}</p>
                <p className="text-xs text-muted-foreground">30 分钟内活跃</p>
              </div>
            </div>
          </div>

          {/* 活跃用户表格（窗口内规模有限，不分页） */}
          <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm">
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/60">
                  <TableHead>用户</TableHead>
                  <TableHead>租户</TableHead>
                  <TableHead>最近活跃时间</TableHead>
                  <TableHead>访问 IP</TableHead>
                  <TableHead className="text-right">状态</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((u) => (
                  <TableRow key={u.username}>
                    <TableCell>
                      <div className="flex items-center gap-2.5">
                        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-50 text-xs font-medium text-indigo-500">
                          {(u.nickname || u.username).slice(0, 1).toUpperCase()}
                        </span>
                        <div>
                          <p className="text-sm font-medium text-slate-700">
                            {u.nickname || u.username}
                          </p>
                          <p className="font-mono text-xs text-muted-foreground">@{u.username}</p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-sm">
                      {u.tenantName || (u.tenantId != null ? `#${u.tenantId}` : "-")}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {fmtTime(u.lastActiveTime)}
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {u.lastIp || "-"}
                    </TableCell>
                    <TableCell className="text-right">
                      {u.online ? (
                        <Badge className="bg-emerald-500">在线</Badge>
                      ) : (
                        <Badge variant="secondary">空闲</Badge>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {users.length === 0 && !loading && (
                  <TableRow>
                    <TableCell colSpan={5} className="py-10 text-center text-muted-foreground">
                      <Monitor className="mx-auto mb-2 h-8 w-8 text-slate-300" />
                      暂无活跃用户
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </section>
        </div>
      </div>
    </AppShell>
  );
}

export default function Page() {
  const { ready } = useAuthGuard({ requireTenantAdmin: true });
  if (!ready) return null;
  return <OnlineMonitorPage />;
}
