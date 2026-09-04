"use client";

/**
 * 租户空间：平台管理员完整 CRUD 管理租户；其他角色只读查看自己所属的租户信息。
 * 后端 /api/adminTenant/my 端点对任何登录用户开放（方法级 @PreAuthorize 覆盖类级 admin 限制）。
 */
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Pencil, Plus, Settings2, Trash2, UserCog, ChevronRight, ChevronDown, Folder, File } from "lucide-react";
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
import type { SysTenant, SysUser, SysMenu } from "@/lib/types";

function emptyTenant(): SysTenant {
  return { tenantCode: "", tenantName: "", status: 1, remark: "" };
}

function TenantAdminPage() {
  const isAdmin = useAuthStore((s) => s.isAdmin)();
  const [tenants, setTenants] = useState<SysTenant[]>([]);
  const [editing, setEditing] = useState<SysTenant | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [saving, setSaving] = useState(false);

  // 设置管理员相关状态
  const [settingAdmin, setSettingAdmin] = useState<SysTenant | null>(null);
  const [tenantUsers, setTenantUsers] = useState<SysUser[]>([]);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [loadingUsers, setLoadingUsers] = useState(false);

  // 配置功能模块相关状态
  const [configFeatures, setConfigFeatures] = useState<SysTenant | null>(null);
  const [menuTree, setMenuTree] = useState<SysMenu[]>([]);
  const [checkedMenuIds, setCheckedMenuIds] = useState<Set<number>>(new Set());
  const [expandedNodes, setExpandedNodes] = useState<Set<number>>(new Set());
  const [loadingFeatures, setLoadingFeatures] = useState(false);
  const [savingFeatures, setSavingFeatures] = useState(false);

  // 非管理员：只读查看自己所属租户
  const [myTenant, setMyTenant] = useState<SysTenant | null>(null);
  useEffect(() => {
    if (!isAdmin) {
      api.get<SysTenant>("/api/adminTenant/my")
        .then((res) => setMyTenant(res.data))
        .catch(() => {});
    }
  }, [isAdmin]);

  const load = useCallback(async () => {
    try {
      const res = await api.get<SysTenant[]>("/api/adminTenant/list");
      setTenants(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function save() {
    if (!editing) return;
    if (!editing.tenantCode || !editing.tenantName) {
      toast.error("租户编码与名称必填");
      return;
    }

    // 新增时，如果填写了管理员用户名，则校验密码必填
    if (isNew && (editing.adminUsername?.trim() ?? "") !== "") {
      if (!editing.adminPassword || editing.adminPassword.trim().length < 6) {
        toast.error("管理员密码至少6位");
        return;
      }
    }

    setSaving(true);
    try {
      if (isNew) {
        // 判断是否携带管理员信息
        const hasAdmin = (editing.adminUsername?.trim() ?? "") !== "";
        if (hasAdmin) {
          // 调用新 API：创建租户并创建管理员
          await api.post("/api/adminTenant/withAdmin", editing);
        } else {
          // 传统方式：仅创建租户
          await api.post("/api/adminTenant", editing);
        }
      } else {
        await api.put(`/api/adminTenant/${editing.id}`, editing);
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

  async function remove(t: SysTenant) {
    if (!window.confirm(`删除租户「${t.tenantName}」？`)) return;
    try {
      await api.del(`/api/adminTenant/${t.id}`);
      toast.success("已删除");
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  /** 构建菜单树 */
  function buildMenuTree(menus: SysMenu[]): SysMenu[] {
    const map = new Map<number, SysMenu & { children: SysMenu[] }>();
    const roots: (SysMenu & { children: SysMenu[] })[] = [];
    menus.forEach((m) => map.set(m.id, { ...m, children: [] }));
    map.forEach((m) => {
      if (m.parentId === 0 || !map.has(m.parentId)) roots.push(m);
      else map.get(m.parentId)!.children.push(m);
    });
    return roots;
  }

  /** 打开配置功能模块 */
  async function openFeatureConfig(tenant: SysTenant) {
    setConfigFeatures(tenant);
    setLoadingFeatures(true);
    try {
      const [allMenus, featureIds] = await Promise.all([
        api.get<SysMenu[]>("/api/adminMenu/list"),
        api.get<number[] | null>(`/api/admin/tenantFeature/${tenant.id}`),
      ]);
      const menus = allMenus.data || [];
      setMenuTree(buildMenuTree(menus));
      // null 表示未配置（全部启用），用所有菜单ID
      const ids = featureIds.data ?? menus.map((m: SysMenu) => m.id);
      setCheckedMenuIds(new Set(ids));
      // 默认展开所有有子节点的目录
      const expandIds = new Set<number>();
      menus.forEach((m: SysMenu) => {
        if (m.menuType === "M") expandIds.add(m.id);
      });
      setExpandedNodes(expandIds);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载功能配置失败");
    } finally {
      setLoadingFeatures(false);
    }
  }

  /** 递归收集节点及子孙ID */
  function collectDescendantIds(node: SysMenu & { children?: SysMenu[] }): number[] {
    const ids = [node.id];
    (node.children || []).forEach((c) => ids.push(...collectDescendantIds(c as SysMenu & { children?: SysMenu[] })));
    return ids;
  }

  /** 切换菜单勾选（级联子节点） */
  function toggleFeatureMenu(node: SysMenu & { children?: SysMenu[] }) {
    const descendantIds = collectDescendantIds(node);
    const allChecked = descendantIds.every((id) => checkedMenuIds.has(id));
    setCheckedMenuIds((prev) => {
      const next = new Set(prev);
      if (allChecked) descendantIds.forEach((id) => next.delete(id));
      else descendantIds.forEach((id) => next.add(id));
      return next;
    });
  }

  /** 展开/折叠目录 */
  function toggleExpand(id: number) {
    setExpandedNodes((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  /** 保存功能配置 */
  async function saveFeatureConfig() {
    if (!configFeatures) return;
    setSavingFeatures(true);
    try {
      await api.put(`/api/admin/tenantFeature/${configFeatures.id}`, {
        menuIds: Array.from(checkedMenuIds),
      });
      toast.success("功能配置已保存");
      setConfigFeatures(null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSavingFeatures(false);
    }
  }

  /** 渲染菜单树节点 */
  function renderMenuNode(node: SysMenu & { children?: SysMenu[] }, depth: number = 0) {
    const hasChildren = (node.children || []).length > 0;
    const isExpanded = expandedNodes.has(node.id);
    const isChecked = checkedMenuIds.has(node.id);
    const typeIcon = node.menuType === "M"
      ? <Folder className="h-3.5 w-3.5 text-amber-500 shrink-0" />
      : <File className="h-3.5 w-3.5 text-slate-400 shrink-0" />;
    return (
      <div key={node.id}>
        <div
          className="flex items-center gap-2 py-1 hover:bg-slate-50 rounded px-1"
          style={{ paddingLeft: `${depth * 20 + 4}px` }}
        >
          {hasChildren ? (
            <button onClick={() => toggleExpand(node.id)} className="shrink-0 text-slate-400 hover:text-slate-600">
              {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
            </button>
          ) : <span className="w-4 shrink-0" />}
          <label className="flex items-center gap-2 cursor-pointer flex-1 min-w-0">
            <input
              type="checkbox"
              checked={isChecked}
              onChange={() => toggleFeatureMenu(node)}
              className="h-4 w-4 accent-indigo-600 shrink-0"
            />
            {typeIcon}
            <span className="text-sm truncate">{node.menuName}</span>
            {node.perms && <span className="text-xs text-muted-foreground font-mono truncate ml-auto">{node.perms}</span>}
          </label>
        </div>
        {hasChildren && isExpanded && node.children!.map((c) => renderMenuNode(c as SysMenu & { children?: SysMenu[] }, depth + 1))}
      </div>
    );
  }

  /** 打开设置管理员对话框 */
  async function openSetAdmin(tenant: SysTenant) {
    setSettingAdmin(tenant);
    setSelectedUserId(null);
    setLoadingUsers(true);
    try {
      // 调用租户级用户列表接口，直接返回该租户下的用户（平台管理员可跨租户查询）
      const res = await api.get<SysUser[]>(`/api/adminTenant/${tenant.id}/users`);
      setTenantUsers(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载用户列表失败");
      setTenantUsers([]);
    } finally {
      setLoadingUsers(false);
    }
  }

  /** 确认设置管理员 */
  async function confirmSetAdmin() {
    if (!settingAdmin || !selectedUserId) {
      toast.error("请选择一个用户");
      return;
    }
    try {
      await api.put(`/api/adminTenant/${settingAdmin.id}/admin`, { userId: selectedUserId });
      toast.success("已设置租户管理员");
      setSettingAdmin(null);
      setSelectedUserId(null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "设置失败");
    }
  }

  if (!isAdmin) {
    return (
      <AppShell>
        <div className="h-full overflow-auto p-6">
          <div className="space-y-4">
            <div>
              <h1 className="text-xl font-semibold text-slate-800">租户空间</h1>
              <p className="text-sm text-slate-500">查看当前所属组织信息</p>
            </div>
            {myTenant ? (
              <div className="rounded-2xl border border-slate-200/70 bg-white p-6 shadow-sm">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <p className="text-xs text-muted-foreground">租户ID</p>
                    <p className="font-mono text-sm font-medium text-slate-700">{myTenant.id}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">租户编码</p>
                    <p className="font-mono text-sm font-medium text-slate-700">{myTenant.tenantCode}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">租户名称</p>
                    <p className="text-sm font-medium text-slate-700">{myTenant.tenantName}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">状态</p>
                    <Badge variant={myTenant.status === 1 ? "default" : "secondary"}>
                      {myTenant.status === 1 ? "启用" : "禁用"}
                    </Badge>
                  </div>
                  {myTenant.remark && (
                    <div className="sm:col-span-2">
                      <p className="text-xs text-muted-foreground">备注</p>
                      <p className="text-sm text-slate-600">{myTenant.remark}</p>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div className="rounded-2xl border border-slate-200/70 bg-white p-10 text-center shadow-sm">
                <p className="text-sm text-muted-foreground">暂无租户信息</p>
              </div>
            )}
          </div>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="h-full overflow-auto p-6">
        <div className="space-y-4">
          {/* 页头 */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-semibold text-slate-800">租户空间</h1>
              <p className="text-sm text-slate-500">
                维护平台租户（组织树与数据隔离的根）
              </p>
            </div>
            <Button
              className="gap-1 bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600"
              onClick={() => {
                setEditing(emptyTenant());
                setIsNew(true);
              }}
            >
              <Plus className="h-4 w-4" /> 新增租户
            </Button>
          </div>

          {/* 租户表格卡片 */}
          <div className="rounded-2xl border border-slate-200/70 bg-white shadow-sm">
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/60">
                  <TableHead>ID</TableHead>
                  <TableHead>编码</TableHead>
                  <TableHead>名称</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>备注</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenants.map((t) => (
                  <TableRow key={t.id}>
                    <TableCell className="text-muted-foreground">{t.id}</TableCell>
                    <TableCell className="font-mono text-xs">{t.tenantCode}</TableCell>
                    <TableCell className="font-medium">{t.tenantName}</TableCell>
                    <TableCell>
                      <Badge variant={t.status === 1 ? "default" : "secondary"}>
                        {t.status === 1 ? "启用" : "禁用"}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-48 truncate text-sm text-muted-foreground">
                      {t.remark || "-"}
                    </TableCell>
                    <TableCell className="text-right">
                      {/* 仅平台管理员可操作 */}
                      {isAdmin && (
                        <>
                          <Button
                            variant="ghost"
                            size="icon"
                            title="配置功能模块"
                            onClick={() => openFeatureConfig(t)}
                          >
                            <Settings2 className="h-4 w-4 text-emerald-500" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            title="设置管理员"
                            onClick={() => openSetAdmin(t)}
                          >
                            <UserCog className="h-4 w-4 text-indigo-500" />
                          </Button>
                        </>
                      )}
                      <Button
                        variant="ghost"
                        size="icon"
                        title="编辑"
                        onClick={() => {
                          setEditing({ ...t });
                          setIsNew(false);
                        }}
                      >
                        <Pencil className="h-4 w-4 text-slate-500" />
                      </Button>
                      <Button variant="ghost" size="icon" title="删除" onClick={() => remove(t)}>
                        <Trash2 className="h-4 w-4 text-rose-500" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {tenants.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                      暂无数据
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      </div>

      {/* 租户编辑：右侧抽屉 */}
      <Sheet open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>{isNew ? "新增租户" : "编辑租户"}</SheetTitle>
            <SheetDescription>
              租户是组织树与数据隔离的根；新注册账号默认归属主租户。
            </SheetDescription>
          </SheetHeader>
          {editing && (
            <div className="flex-1 space-y-5 overflow-y-auto py-2">
              <div className="space-y-1.5">
                <Label>
                  租户编码（创建后不可改） <span className="text-rose-500">*</span>
                </Label>
                <Input
                  disabled={!isNew}
                  placeholder="英文标识，如 acme"
                  className="font-mono"
                  value={editing.tenantCode}
                  onChange={(e) => setEditing({ ...editing, tenantCode: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>
                  租户名称 <span className="text-rose-500">*</span>
                </Label>
                <Input
                  value={editing.tenantName}
                  onChange={(e) => setEditing({ ...editing, tenantName: e.target.value })}
                />
              </div>
              <div className="flex items-center gap-2">
                <Switch
                  checked={editing.status === 1}
                  onCheckedChange={(c) => setEditing({ ...editing, status: c ? 1 : 0 })}
                />
                <span className="text-sm text-slate-600">启用</span>
              </div>
              <div className="space-y-1.5">
                <Label>备注</Label>
                <Input
                  value={editing.remark || ""}
                  onChange={(e) => setEditing({ ...editing, remark: e.target.value })}
                />
              </div>

              {/* 新增租户时显示初始管理员信息 */}
              {isNew && (
                <>
                  <div className="border-t pt-4 mt-6">
                    <h3 className="text-sm font-medium text-slate-700 mb-3">初始管理员信息（可选）</h3>
                    <p className="text-xs text-muted-foreground mb-3">
                      填写后将自动创建该用户并授予租户管理员角色
                    </p>
                  </div>
                  <div className="space-y-1.5">
                    <Label>管理员用户名</Label>
                    <Input
                      placeholder="留空则不创建管理员"
                      value={editing.adminUsername || ""}
                      onChange={(e) => setEditing({ ...editing, adminUsername: e.target.value })}
                    />
                  </div>
                  {(editing.adminUsername?.trim() ?? "") !== "" && (
                    <>
                      <div className="space-y-1.5">
                        <Label>
                          管理员密码 <span className="text-rose-500">*</span>
                        </Label>
                        <Input
                          type="password"
                          placeholder="至少6位"
                          value={editing.adminPassword || ""}
                          onChange={(e) => setEditing({ ...editing, adminPassword: e.target.value })}
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label>管理员昵称</Label>
                        <Input
                          value={editing.adminNickname || ""}
                          onChange={(e) => setEditing({ ...editing, adminNickname: e.target.value })}
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label>手机号</Label>
                        <Input
                          value={editing.adminPhone || ""}
                          onChange={(e) => setEditing({ ...editing, adminPhone: e.target.value })}
                        />
                      </div>
                      <div className="space-y-1.5">
                        <Label>邮箱</Label>
                        <Input
                          type="email"
                          value={editing.adminEmail || ""}
                          onChange={(e) => setEditing({ ...editing, adminEmail: e.target.value })}
                        />
                      </div>
                    </>
                  )}
                </>
              )}
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

      {/* 配置功能模块 */}
      <Sheet open={!!configFeatures} onOpenChange={(open) => !open && setConfigFeatures(null)}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>配置功能模块</SheetTitle>
            <SheetDescription>
              为「{configFeatures?.tenantName}」配置可用的功能模块，租户管理员只能使用已启用的功能
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-y-auto py-2">
            {loadingFeatures ? (
              <p className="py-10 text-center text-sm text-muted-foreground">加载中...</p>
            ) : (
              <div className="space-y-0.5">
                {menuTree.map((node) => renderMenuNode(node as SysMenu & { children?: SysMenu[] }))}
                {menuTree.length === 0 && (
                  <p className="py-10 text-center text-sm text-muted-foreground">暂无菜单数据</p>
                )}
              </div>
            )}
          </div>
          <SheetFooter className="border-t pt-4">
            <span className="text-xs text-muted-foreground mr-auto">
              已选 {checkedMenuIds.size} 项
            </span>
            <Button variant="outline" onClick={() => setConfigFeatures(null)}>
              取消
            </Button>
            <Button onClick={saveFeatureConfig} disabled={savingFeatures || loadingFeatures} className="min-w-24">
              {savingFeatures ? "保存中..." : "保存"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>

      {/* 设置管理员对话框 */}
      <Sheet open={!!settingAdmin} onOpenChange={(open) => !open && setSettingAdmin(null)}>
        <SheetContent className="sm:max-w-md">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>设置租户管理员</SheetTitle>
            <SheetDescription>
              {settingAdmin ? `为「${settingAdmin.tenantName}」选择一名管理员` : ""}
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 space-y-4 overflow-y-auto py-2">
            {loadingUsers ? (
              <p className="text-center text-sm text-muted-foreground">加载中...</p>
            ) : tenantUsers.length === 0 ? (
              <p className="text-center text-sm text-muted-foreground">
                该租户下暂无用户，请先创建用户
              </p>
            ) : (
              <div className="space-y-2">
                {tenantUsers.map((user) => (
                  <label
                    key={user.id}
                    className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors ${
                      selectedUserId === user.id
                        ? "border-indigo-500 bg-indigo-50"
                        : "border-slate-200 hover:bg-slate-50"
                    }`}
                  >
                    <input
                      type="radio"
                      name="adminUser"
                      checked={selectedUserId === user.id}
                      onChange={() => setSelectedUserId(user.id)}
                      className="h-4 w-4 accent-indigo-600"
                    />
                    <div className="flex-1">
                      <p className="font-medium text-slate-800">{user.nickname || user.username}</p>
                      <p className="text-xs text-muted-foreground">{user.username}</p>
                    </div>
                  </label>
                ))}
              </div>
            )}
          </div>
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setSettingAdmin(null)}>
              取消
            </Button>
            <Button
              onClick={confirmSetAdmin}
              disabled={!selectedUserId || loadingUsers}
              className="min-w-24"
            >
              确认
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
  return <TenantAdminPage />;
}
