"use client";

/**
 * 审计日志：业务操作日志 + 登录登出日志（双 Tab，只读，各自独立分页 + 搜索筛选）。
 * 权限：菜单隐藏 + 页面守卫（租户管理员及以上）+ 后端 /api/admin/** 角色校验三重防护。
 */
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { RefreshCw, Search } from "lucide-react";
import AppShell from "@/components/app-shell";
import Pagination from "@/components/pagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select";
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
import type { LoginLog, OperLog, PageResult } from "@/lib/types";

/** 日志每页条数（后端默认 20，前端保持一致） */
const PAGE_SIZE = 20;

/** 时间展示：后端已统一下发「yyyy-MM-dd HH:mm:ss」，空值显示横杠 */
function fmtTime(t?: string | null): string {
  return t || "-";
}

/** 操作类型中文映射 */
const OPER_TYPE_LABEL: Record<string, string> = {
  all: "全部类型",
  CREATE: "新增",
  UPDATE: "修改",
  DELETE: "删除",
  GRANT: "授权",
  OTHER: "其他",
};

/** 状态中文映射 */
const STATUS_LABEL: Record<string, string> = {
  all: "全部状态",
  "1": "成功",
  "0": "失败",
};

/** 事件类型中文映射 */
const EVENT_TYPE_LABEL: Record<string, string> = {
  all: "全部事件",
  LOGIN: "登录",
  LOGOUT: "登出",
};

type TabKey = "oper" | "login";

function LogAdminPage() {
  const [tab, setTab] = useState<TabKey>("oper");
  const [operLogs, setOperLogs] = useState<OperLog[]>([]);
  const [loginLogs, setLoginLogs] = useState<LoginLog[]>([]);
  const [loading, setLoading] = useState(false);
  // 双 Tab 独立页码与总数
  const [operPage, setOperPage] = useState(1);
  const [operTotal, setOperTotal] = useState(0);
  const [loginPage, setLoginPage] = useState(1);
  const [loginTotal, setLoginTotal] = useState(0);

  // 操作日志搜索筛选
  const [operKeyword, setOperKeyword] = useState("");
  const [operTypeFilter, setOperTypeFilter] = useState<string>("all");
  const [operStatusFilter, setOperStatusFilter] = useState<string>("all");

  // 登录日志搜索筛选
  const [loginKeyword, setLoginKeyword] = useState("");
  const [loginEventType, setLoginEventType] = useState<string>("all");
  const [loginStatusFilter, setLoginStatusFilter] = useState<string>("all");

  const loadOper = useCallback(async (pageNum: number) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        pageNum: String(pageNum),
        pageSize: String(PAGE_SIZE),
      });
      if (operKeyword.trim()) params.set("keyword", operKeyword.trim());
      if (operTypeFilter !== "all") params.set("operType", operTypeFilter);
      if (operStatusFilter !== "all") params.set("status", operStatusFilter);
      const res = await api.get<PageResult<OperLog>>(
        `/api/adminLog/oper/page?${params.toString()}`
      );
      setOperLogs(res.data?.records || []);
      setOperTotal(res.data?.total || 0);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }, [operKeyword, operTypeFilter, operStatusFilter]);

  const loadLogin = useCallback(async (pageNum: number) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        pageNum: String(pageNum),
        pageSize: String(PAGE_SIZE),
      });
      if (loginKeyword.trim()) params.set("keyword", loginKeyword.trim());
      if (loginEventType !== "all") params.set("eventType", loginEventType);
      if (loginStatusFilter !== "all") params.set("status", loginStatusFilter);
      const res = await api.get<PageResult<LoginLog>>(
        `/api/adminLog/login/page?${params.toString()}`
      );
      setLoginLogs(res.data?.records || []);
      setLoginTotal(res.data?.total || 0);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }, [loginKeyword, loginEventType, loginStatusFilter]);

  // 按当前 Tab 与页码拉取；切 Tab 时沿用该 Tab 上次停留的页码，避免每次回首页
  useEffect(() => {
    if (tab === "oper") loadOper(operPage);
    else loadLogin(loginPage);
  }, [tab, operPage, loginPage, loadOper, loadLogin]);

  /** 刷新：回到各 Tab 首页重新拉取 */
  const refresh = () => {
    setOperPage(1);
    setLoginPage(1);
    if (tab === "oper") loadOper(1);
    else loadLogin(1);
  };

  return (
    <AppShell>
      <div className="h-full overflow-auto p-6">
        <div className="space-y-4">
          {/* 页头 */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-semibold text-slate-800">审计日志</h1>
              <p className="text-sm text-slate-500">
                业务操作日志与登录登出日志（按时间倒序，分页查阅）
              </p>
            </div>
            <Button variant="outline" className="gap-1" onClick={refresh} disabled={loading}>
              <RefreshCw className={"h-4 w-4" + (loading ? " animate-spin" : "")} /> 刷新
            </Button>
          </div>

          {/* Tab 切换 */}
          <div className="flex gap-1 rounded-full border border-slate-200/70 bg-white p-1 shadow-sm w-fit">
            {(
              [
                { key: "oper", label: "操作日志" },
                { key: "login", label: "登录日志" },
              ] as { key: TabKey; label: string }[]
            ).map((t) => (
              <button
                key={t.key}
                onClick={() => setTab(t.key)}
                className={
                  "rounded-full px-5 py-1.5 text-sm transition-colors " +
                  (tab === t.key
                    ? "bg-slate-800 text-white shadow-sm"
                    : "text-slate-600 hover:bg-slate-100")
                }
              >
                {t.label}
              </button>
            ))}
          </div>

          {/* 操作日志搜索筛选栏 */}
          {tab === "oper" && (
            <div className="flex flex-wrap items-center gap-2 rounded-2xl border border-slate-200/70 bg-white p-3 shadow-sm">
              <div className="relative flex-1 min-w-[200px] max-w-sm">
                <Search className="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <Input
                  className="pl-8"
                  placeholder="搜索操作人 / 模块 / 描述"
                  value={operKeyword}
                  onChange={(e) => setOperKeyword(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && setOperPage(1)}
                />
              </div>
              <Select value={operTypeFilter} onValueChange={(v) => { if (v != null) { setOperTypeFilter(v); setOperPage(1); } }}>
                <SelectTrigger className="w-[110px]">
                  <span className="truncate">{OPER_TYPE_LABEL[operTypeFilter] || operTypeFilter}</span>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部类型</SelectItem>
                  <SelectItem value="CREATE">新增</SelectItem>
                  <SelectItem value="UPDATE">修改</SelectItem>
                  <SelectItem value="DELETE">删除</SelectItem>
                  <SelectItem value="GRANT">授权</SelectItem>
                  <SelectItem value="OTHER">其他</SelectItem>
                </SelectContent>
              </Select>
              <Select value={operStatusFilter} onValueChange={(v) => { if (v != null) { setOperStatusFilter(v); setOperPage(1); } }}>
                <SelectTrigger className="w-[110px]">
                  <span className="truncate">{STATUS_LABEL[operStatusFilter] || operStatusFilter}</span>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部状态</SelectItem>
                  <SelectItem value="1">成功</SelectItem>
                  <SelectItem value="0">失败</SelectItem>
                </SelectContent>
              </Select>
              <Button variant="outline" className="gap-1" onClick={() => setOperPage(1)}>
                <Search className="h-3.5 w-3.5" /> 搜索
              </Button>
            </div>
          )}

          {/* 操作日志表 */}
          {tab === "oper" && (
            <div className="rounded-2xl border border-slate-200/70 bg-white shadow-sm">
              <Table>
                <TableHeader>
                  <TableRow className="bg-slate-50/60">
                    <TableHead>时间</TableHead>
                    <TableHead>操作人</TableHead>
                    <TableHead>IP</TableHead>
                    <TableHead>模块</TableHead>
                    <TableHead>类型</TableHead>
                    <TableHead>描述</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>失败原因</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {operLogs.map((log) => (
                    <TableRow key={log.id}>
                      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                        {fmtTime(log.operTime)}
                      </TableCell>
                      <TableCell className="font-medium">{log.operName}</TableCell>
                      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                        {log.ip || "-"}
                      </TableCell>
                      <TableCell>{log.module}</TableCell>
                      <TableCell>
                        <Badge variant="secondary">{OPER_TYPE_LABEL[log.operType] || log.operType}</Badge>
                      </TableCell>
                      <TableCell>{log.operDesc}</TableCell>
                      <TableCell>
                        <Badge variant={log.status === 1 ? "default" : "destructive"}>
                          {log.status === 1 ? "成功" : "失败"}
                        </Badge>
                      </TableCell>
                      <TableCell className="max-w-56 truncate text-sm text-rose-500" title={log.errorMsg || ""}>
                        {log.errorMsg || "-"}
                      </TableCell>
                    </TableRow>
                  ))}
                  {operLogs.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={8} className="py-8 text-center text-muted-foreground">
                        暂无数据
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <Pagination page={operPage} pageSize={PAGE_SIZE} total={operTotal} onChange={setOperPage} />
            </div>
          )}

          {/* 登录日志搜索筛选栏 */}
          {tab === "login" && (
            <div className="flex flex-wrap items-center gap-2 rounded-2xl border border-slate-200/70 bg-white p-3 shadow-sm">
              <div className="relative flex-1 min-w-[200px] max-w-sm">
                <Search className="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <Input
                  className="pl-8"
                  placeholder="搜索用户名"
                  value={loginKeyword}
                  onChange={(e) => setLoginKeyword(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && setLoginPage(1)}
                />
              </div>
              <Select value={loginEventType} onValueChange={(v) => { if (v != null) { setLoginEventType(v); setLoginPage(1); } }}>
                <SelectTrigger className="w-[110px]">
                  <span className="truncate">{EVENT_TYPE_LABEL[loginEventType] || loginEventType}</span>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部事件</SelectItem>
                  <SelectItem value="LOGIN">登录</SelectItem>
                  <SelectItem value="LOGOUT">登出</SelectItem>
                </SelectContent>
              </Select>
              <Select value={loginStatusFilter} onValueChange={(v) => { if (v != null) { setLoginStatusFilter(v); setLoginPage(1); } }}>
                <SelectTrigger className="w-[110px]">
                  <span className="truncate">{STATUS_LABEL[loginStatusFilter] || loginStatusFilter}</span>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部状态</SelectItem>
                  <SelectItem value="1">成功</SelectItem>
                  <SelectItem value="0">失败</SelectItem>
                </SelectContent>
              </Select>
              <Button variant="outline" className="gap-1" onClick={() => setLoginPage(1)}>
                <Search className="h-3.5 w-3.5" /> 搜索
              </Button>
            </div>
          )}

          {/* 登录日志表 */}
          {tab === "login" && (
            <div className="rounded-2xl border border-slate-200/70 bg-white shadow-sm">
              <Table>
                <TableHeader>
                  <TableRow className="bg-slate-50/60">
                    <TableHead>时间</TableHead>
                    <TableHead>用户名</TableHead>
                    <TableHead>IP</TableHead>
                    <TableHead>事件</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>提示</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {loginLogs.map((log) => (
                    <TableRow key={log.id}>
                      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                        {fmtTime(log.loginTime)}
                      </TableCell>
                      <TableCell className="font-medium">{log.username}</TableCell>
                      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                        {log.ip || "-"}
                      </TableCell>
                      <TableCell>
                        <Badge variant={log.eventType === "LOGIN" ? "default" : "secondary"}>
                          {log.eventType === "LOGIN" ? "登录" : "登出"}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant={log.status === 1 ? "default" : "destructive"}>
                          {log.status === 1 ? "成功" : "失败"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">{log.msg || "-"}</TableCell>
                    </TableRow>
                  ))}
                  {loginLogs.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                        暂无数据
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <Pagination page={loginPage} pageSize={PAGE_SIZE} total={loginTotal} onChange={setLoginPage} />
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}

export default function Page() {
  const { ready } = useAuthGuard({ requireTenantAdmin: true });
  if (!ready) return null;
  return <LogAdminPage />;
}
