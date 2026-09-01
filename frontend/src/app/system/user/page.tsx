"use client";

/**
 * 用户管理：本租户用户列表 / 新增 / 修改状态 / 重置密码 / 删除 / 分配角色。
 * 仅租户管理员及以上可见（菜单与后端 /api/admin/** 双重校验）。
 */
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { KeyRound, Plus, Search, Trash2, UserCog } from "lucide-react";
import AppShell from "@/components/app-shell";
import Pagination from "@/components/pagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
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
import { useAuthStore } from "@/store/auth";
import type { PageResult, SysDept, SysRole, SysUser, UserCreateRequest } from "@/lib/types";

/** 用户列表每页条数 */
const PAGE_SIZE = 10;

function UserAdminPage() {
  const hasPerm = useAuthStore((s) => s.hasPerm);
  const [users, setUsers] = useState<SysUser[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [addOpen, setAddOpen] = useState(false);
  const [addForm, setAddForm] = useState<UserCreateRequest>({ username: "", password: "" });
  const [pwdTarget, setPwdTarget] = useState<SysUser | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [saving, setSaving] = useState(false);

  // 搜索与筛选状态
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [deptFilter, setDeptFilter] = useState<string>("all");

  // 部门列表（搜索栏下拉）
  const [depts, setDepts] = useState<SysDept[]>([]);

  // 分配角色对话框状态
  const [roleTarget, setRoleTarget] = useState<SysUser | null>(null);
  const [allRoles, setAllRoles] = useState<SysRole[]>([]);
  const [checkedRoleIds, setCheckedRoleIds] = useState<number[]>([]);

  // 页面初始化：拉取部门列表（仅一次）
  useEffect(() => {
    api.get<SysDept[]>("/api/adminDept/list").then((res) => {
      setDepts(res.data || []);
    }).catch(() => { /* 部门加载失败不阻塞页面 */ });
  }, []);

  const load = useCallback(async (pageNum: number) => {
    try {
      const params = new URLSearchParams({
        pageNum: String(pageNum),
        pageSize: String(PAGE_SIZE),
      });
      if (keyword.trim()) params.set("keyword", keyword.trim());
      if (statusFilter !== "all") params.set("status", statusFilter);
      if (deptFilter !== "all") params.set("deptId", deptFilter);
      const res = await api.get<PageResult<SysUser>>(
        `/api/adminUser/page?${params.toString()}`
      );
      setUsers(res.data?.records || []);
      setTotal(res.data?.total || 0);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    }
  }, [keyword, statusFilter, deptFilter]);

  useEffect(() => {
    load(page);
  }, [load, page]);

  /** 搜索：重置到首页并触发加载 */
  const doSearch = () => {
    setPage(1);
  };

  async function addUser() {
    if (!addForm.username || !addForm.password) {
      toast.error("用户名与密码必填");
      return;
    }
    setSaving(true);
    try {
      await api.post("/api/adminUser", addForm);
      toast.success("用户已创建");
      setAddOpen(false);
      setAddForm({ username: "", password: "" });
      load(page);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "创建失败");
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(u: SysUser) {
    try {
      // 后端更新会按请求体重写联系方式字段，必须把现有值原样带回，否则开关状态会误清空手机/邮箱/性别
      await api.put(`/api/adminUser/${u.id}`, {
        nickname: u.nickname,
        phone: u.phone ?? null,
        email: u.email ?? null,
        gender: u.gender ?? 0,
        deptId: u.deptId,
        status: u.status === 1 ? 0 : 1,
        remark: u.remark,
      });
      load(page);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "操作失败");
    }
  }

  async function resetPassword() {
    if (!pwdTarget || newPassword.length < 6) {
      toast.error("新密码至少 6 位");
      return;
    }
    setSaving(true);
    try {
      await api.put(`/api/adminUser/${pwdTarget.id}/password`, { newPassword });
      toast.success("密码已重置");
      setPwdTarget(null);
      setNewPassword("");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "重置失败");
    } finally {
      setSaving(false);
    }
  }

  async function removeUser(u: SysUser) {
    if (!window.confirm(`删除用户 ${u.username}？`)) return;
    try {
      await api.del(`/api/adminUser/${u.id}`);
      toast.success("已删除");
      // 删掉本页最后一条时回退一页，避免停留在空页
      if (users.length === 1 && page > 1) {
        setPage(page - 1);
      } else {
        load(page);
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  /** 打开分配角色对话框：加载本租户角色 + 该用户已分配角色 */
  async function openRoleAssign(u: SysUser) {
    try {
      const [roles, assigned] = await Promise.all([
        api.get<SysRole[]>("/api/adminRole/list"),
        api.get<number[]>(`/api/adminUser/${u.id}/roles`),
      ]);
      setAllRoles(roles.data || []);
      setCheckedRoleIds(assigned.data || []);
      setRoleTarget(u);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载角色数据失败");
    }
  }

  function toggleRole(roleId: number) {
    setCheckedRoleIds((ids) =>
      ids.includes(roleId) ? ids.filter((i) => i !== roleId) : [...ids, roleId]
    );
  }

  async function saveRoleAssign() {
    if (!roleTarget) return;
    setSaving(true);
    try {
      await api.put(`/api/adminUser/${roleTarget.id}/roles`, { roleIds: checkedRoleIds });
      toast.success("角色已分配（用户下次登录生效）");
      setRoleTarget(null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <AppShell>
      <div className="h-full overflow-auto p-6">
        <div className="space-y-4">
          {/* 页头 */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-semibold text-slate-800">成员与账户</h1>
              <p className="text-sm text-slate-500">维护本租户的用户账号与启用状态</p>
            </div>
            {/* 新增按钮按权限点显隐（后端同步拦截） */}
            {hasPerm("system:user:add") && (
              <Button
                className="gap-1 bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600"
                onClick={() => setAddOpen(true)}
              >
                <Plus className="h-4 w-4" /> 新增用户
              </Button>
            )}
          </div>

          {/* 搜索与筛选栏 */}
          <div className="flex flex-wrap items-center gap-2 rounded-2xl border border-slate-200/70 bg-white p-3 shadow-sm">
            <div className="relative flex-1 min-w-[200px] max-w-sm">
              <Search className="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <Input
                className="pl-8"
                placeholder="搜索用户名 / 昵称 / 手机 / 邮箱"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && doSearch()}
              />
            </div>
            <Select value={statusFilter} onValueChange={(v) => { if (v != null) { setStatusFilter(v); setPage(1); } }}>
              <SelectTrigger className="w-[110px]">
                <span className="truncate">{statusFilter === "all" ? "全部状态" : statusFilter === "1" ? "启用" : statusFilter === "0" ? "禁用" : "状态"}</span>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部状态</SelectItem>
                <SelectItem value="1">启用</SelectItem>
                <SelectItem value="0">禁用</SelectItem>
              </SelectContent>
            </Select>
            <Select value={deptFilter} onValueChange={(v) => { if (v != null) { setDeptFilter(v); setPage(1); } }}>
              <SelectTrigger className="w-[140px]">
                <span className="truncate">{deptFilter === "all" ? "全部部门" : (depts.find((d) => String(d.id) === deptFilter)?.deptName || "部门")}</span>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部部门</SelectItem>
                {depts.map((d) => (
                  <SelectItem key={d.id} value={String(d.id)}>{d.deptName}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button variant="outline" className="gap-1" onClick={doSearch}>
              <Search className="h-3.5 w-3.5" /> 搜索
            </Button>
          </div>

          {/* 用户表格卡片 */}
          <div className="rounded-2xl border border-slate-200/70 bg-white shadow-sm">
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/60">
                  <TableHead>ID</TableHead>
                  <TableHead>用户名</TableHead>
                  <TableHead>昵称</TableHead>
                  <TableHead>手机号码</TableHead>
                  <TableHead>邮箱</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>创建时间</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((u) => (
                  <TableRow key={u.id}>
                    <TableCell className="text-muted-foreground">{u.id}</TableCell>
                    <TableCell className="font-medium">{u.username}</TableCell>
                    <TableCell>{u.nickname || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">{u.phone || "-"}</TableCell>
                    <TableCell className="text-muted-foreground">{u.email || "-"}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Switch
                          checked={u.status === 1}
                          disabled={!hasPerm("system:user:edit")}
                          onCheckedChange={() => toggleStatus(u)}
                        />
                        <Badge variant={u.status === 1 ? "default" : "secondary"}>
                          {u.status === 1 ? "启用" : "禁用"}
                        </Badge>
                      </div>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {/* 后端已统一下发 yyyy-MM-dd HH:mm:ss */}
                      {u.createTime || "-"}
                    </TableCell>
                    <TableCell className="text-right">
                      {hasPerm("system:user:grant") && (
                        <Button variant="ghost" size="icon" title="分配角色" onClick={() => openRoleAssign(u)}>
                          <UserCog className="h-4 w-4 text-indigo-500" />
                        </Button>
                      )}
                      {hasPerm("system:user:resetPwd") && (
                        <Button variant="ghost" size="icon" title="重置密码" onClick={() => setPwdTarget(u)}>
                          <KeyRound className="h-4 w-4 text-slate-500" />
                        </Button>
                      )}
                      {hasPerm("system:user:remove") && (
                        <Button variant="ghost" size="icon" title="删除" onClick={() => removeUser(u)}>
                          <Trash2 className="h-4 w-4 text-rose-500" />
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {users.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={8} className="py-8 text-center text-muted-foreground">
                      暂无数据
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
            {/* 分页：后端分页接口驱动，翻页只拉当页数据 */}
            <Pagination page={page} pageSize={PAGE_SIZE} total={total} onChange={setPage} />
          </div>
        </div>
      </div>

      {/* 新增用户：右侧抽屉 */}
      <Sheet open={addOpen} onOpenChange={setAddOpen}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>新增用户</SheetTitle>
            <SheetDescription>创建后归属本租户，可在列表中为其分配角色</SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-5 overflow-y-auto py-2">
            <div className="space-y-1.5">
              <Label>
                用户名 <span className="text-rose-500">*</span>
              </Label>
              <Input
                placeholder="字母开头，4-32 位字母数字下划线"
                value={addForm.username}
                onChange={(e) => setAddForm({ ...addForm, username: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label>
                密码 <span className="text-rose-500">*</span>
              </Label>
              <Input
                type="password"
                placeholder="至少 6 位"
                value={addForm.password}
                onChange={(e) => setAddForm({ ...addForm, password: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label>昵称（可选）</Label>
              <Input
                value={addForm.nickname || ""}
                onChange={(e) => setAddForm({ ...addForm, nickname: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label>手机号码（可选）</Label>
              <Input
                placeholder="11 位，1 开头"
                value={addForm.phone || ""}
                onChange={(e) => setAddForm({ ...addForm, phone: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label>邮箱（可选）</Label>
              <Input
                placeholder="user@example.com"
                value={addForm.email || ""}
                onChange={(e) => setAddForm({ ...addForm, email: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label>性别</Label>
              <select
                className="h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                value={addForm.gender ?? 0}
                onChange={(e) => setAddForm({ ...addForm, gender: Number(e.target.value) })}
              >
                <option value={0}>未知</option>
                <option value={1}>男</option>
                <option value={2}>女</option>
              </select>
            </div>
          </div>
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setAddOpen(false)}>
              取消
            </Button>
            <Button onClick={addUser} disabled={saving} className="min-w-24">
              {saving ? "创建中..." : "创建"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>

      {/* 重置密码：右侧抽屉 */}
      <Sheet open={!!pwdTarget} onOpenChange={(open) => !open && setPwdTarget(null)}>
        <SheetContent className="sm:max-w-md">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>重置密码 - {pwdTarget?.username}</SheetTitle>
            <SheetDescription>重置后用户需使用新密码重新登录</SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-1.5 overflow-y-auto py-2">
            <Label>新密码</Label>
            <Input
              type="password"
              placeholder="至少 6 位"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
          </div>
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setPwdTarget(null)}>
              取消
            </Button>
            <Button onClick={resetPassword} disabled={saving} className="min-w-24">
              {saving ? "重置中..." : "确认重置"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>

      {/* 分配角色：右侧抽屉 */}
      <Sheet open={!!roleTarget} onOpenChange={(open) => !open && setRoleTarget(null)}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>分配角色 - {roleTarget?.username}</SheetTitle>
            <SheetDescription>勾选该用户拥有的角色，保存后下次登录生效</SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-1 overflow-y-auto rounded-lg border bg-slate-50/40 p-2">
            {allRoles.map((r) => (
              <label
                key={r.id}
                className="flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 text-sm hover:bg-slate-100/70"
              >
                <input
                  type="checkbox"
                  className="h-4 w-4 accent-indigo-500"
                  checked={r.id != null && checkedRoleIds.includes(r.id)}
                  onChange={() => r.id != null && toggleRole(r.id)}
                />
                <span className="font-medium text-slate-700">{r.roleName}</span>
                <span className="font-mono text-xs text-muted-foreground">{r.roleKey}</span>
                {r.status === 0 && (
                  <span className="text-xs text-amber-500">（已禁用）</span>
                )}
              </label>
            ))}
            {allRoles.length === 0 && (
              <p className="py-6 text-center text-sm text-muted-foreground">本租户暂无角色</p>
            )}
          </div>
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setRoleTarget(null)}>
              取消
            </Button>
            <Button onClick={saveRoleAssign} disabled={saving} className="min-w-24">
              {saving ? "保存中..." : "保存分配"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </AppShell>
  );
}

export default function Page() {
  const { ready } = useAuthGuard({ requireTenantAdmin: true });
  if (!ready) return null;
  return <UserAdminPage />;
}
