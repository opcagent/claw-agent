"use client";

/**
 * 角色管理：本租户角色列表 / 新增 / 编辑 / 删除 + 菜单权限授权。
 * 授权对话框按菜单树勾选（目录/菜单/按钮三级），全量替换保存。
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { KeySquare, Pencil, Plus, Trash2 } from "lucide-react";
import AppShell from "@/components/app-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
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
import type { SysMenu, SysRole } from "@/lib/types";

/** 数据权限五档（若依约定，与 Role.dataScope 对齐） */
const DATA_SCOPES = [
  { value: 1, label: "全部数据" },
  { value: 2, label: "自定义（角色-部门）" },
  { value: 3, label: "本部门" },
  { value: 4, label: "本部门及以下" },
  { value: 5, label: "仅本人" },
];

const MENU_TYPE_LABEL: Record<string, string> = { M: "目录", C: "菜单", F: "按钮" };

function emptyRole(): SysRole {
  return { roleName: "", roleKey: "", roleSort: 0, dataScope: 5, status: 1, remark: "" };
}

function RoleAdminPage() {
  const hasPerm = useAuthStore((s) => s.hasPerm);
  const [roles, setRoles] = useState<SysRole[]>([]);
  const [editing, setEditing] = useState<SysRole | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [saving, setSaving] = useState(false);

  // 菜单授权对话框状态
  const [authTarget, setAuthTarget] = useState<SysRole | null>(null);
  const [allMenus, setAllMenus] = useState<SysMenu[]>([]);
  const [checkedMenuIds, setCheckedMenuIds] = useState<number[]>([]);

  const load = useCallback(async () => {
    try {
      const res = await api.get<SysRole[]>("/api/adminRole/list");
      setRoles(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** 菜单按父级分组（根目录为一级分组，其余平铺缩进） */
  const menuRows = useMemo(() => {
    const children = new Map<number, SysMenu[]>();
    for (const m of allMenus) {
      const list = children.get(m.parentId) ?? [];
      list.push(m);
      children.set(m.parentId, list);
    }
    const out: { menu: SysMenu; depth: number }[] = [];
    const walk = (parentId: number, depth: number) => {
      for (const m of children.get(parentId) ?? []) {
        out.push({ menu: m, depth });
        walk(m.id, depth + 1);
      }
    };
    walk(0, 0);
    return out;
  }, [allMenus]);

  async function save() {
    if (!editing) return;
    if (!editing.roleName || !editing.roleKey) {
      toast.error("角色名称与权限字符必填");
      return;
    }
    setSaving(true);
    try {
      if (isNew) {
        await api.post("/api/adminRole", editing);
      } else {
        await api.put(`/api/adminRole/${editing.id}`, editing);
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

  async function remove(r: SysRole) {
    if (!window.confirm(`删除角色「${r.roleName}」？`)) return;
    try {
      await api.del(`/api/adminRole/${r.id}`);
      toast.success("已删除");
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  /** 打开菜单授权对话框：加载全部菜单 + 该角色已授权菜单 */
  async function openAuth(r: SysRole) {
    try {
      const [menus, assigned] = await Promise.all([
        api.get<SysMenu[]>("/api/adminMenu/list"),
        api.get<number[]>(`/api/adminRole/${r.id}/menus`),
      ]);
      setAllMenus(menus.data || []);
      setCheckedMenuIds(assigned.data || []);
      setAuthTarget(r);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载授权数据失败");
    }
  }

  function toggleMenu(menuId: number) {
    setCheckedMenuIds((ids) =>
      ids.includes(menuId) ? ids.filter((i) => i !== menuId) : [...ids, menuId]
    );
  }

  async function saveAuth() {
    if (!authTarget) return;
    setSaving(true);
    try {
      await api.put(`/api/adminRole/${authTarget.id}/menus`, { menuIds: checkedMenuIds });
      toast.success("菜单授权已保存");
      setAuthTarget(null);
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
              <h1 className="text-xl font-semibold text-slate-800">角色与权限</h1>
              <p className="text-sm text-slate-500">
                维护本租户角色，并通过菜单授权控制功能权限
              </p>
            </div>
            {/* 新增按钮按权限点显隐（后端同步拦截） */}
            {hasPerm("system:role:add") && (
              <Button
                className="gap-1 bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600"
                onClick={() => {
                  setEditing(emptyRole());
                  setIsNew(true);
                }}
              >
                <Plus className="h-4 w-4" /> 新增角色
              </Button>
            )}
          </div>

          {/* 角色表格卡片 */}
          <div className="rounded-2xl border border-slate-200/70 bg-white shadow-sm">
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/60">
                  <TableHead>角色名称</TableHead>
                  <TableHead>权限字符</TableHead>
                  <TableHead>数据权限</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>备注</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {roles.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="font-medium">{r.roleName}</TableCell>
                    <TableCell className="font-mono text-xs">{r.roleKey}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {DATA_SCOPES.find((s) => s.value === r.dataScope)?.label || "-"}
                    </TableCell>
                    <TableCell>
                      <Badge variant={r.status === 1 ? "default" : "secondary"}>
                        {r.status === 1 ? "启用" : "禁用"}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-40 truncate text-sm text-muted-foreground">
                      {r.remark || "-"}
                    </TableCell>
                    <TableCell className="text-right">
                      {hasPerm("system:role:grant") && (
                        <Button variant="ghost" size="icon" title="菜单授权" onClick={() => openAuth(r)}>
                          <KeySquare className="h-4 w-4 text-indigo-500" />
                        </Button>
                      )}
                      {hasPerm("system:role:edit") && (
                        <Button
                          variant="ghost"
                          size="icon"
                          title="编辑"
                          onClick={() => {
                            setEditing({ ...r });
                            setIsNew(false);
                          }}
                        >
                          <Pencil className="h-4 w-4 text-slate-500" />
                        </Button>
                      )}
                      {hasPerm("system:role:remove") && (
                        <Button variant="ghost" size="icon" title="删除" onClick={() => remove(r)}>
                          <Trash2 className="h-4 w-4 text-rose-500" />
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {roles.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                      暂无数据
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      </div>

      {/* 角色编辑：右侧抽屉 */}
      <Sheet open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>{isNew ? "新增角色" : "编辑角色"}</SheetTitle>
            <SheetDescription>
              创建后通过「菜单授权」分配功能权限；数据权限为五档预留能力。
            </SheetDescription>
          </SheetHeader>
          {editing && (
            <div className="flex-1 space-y-5 overflow-y-auto py-2">
              <div className="space-y-1.5">
                <Label>
                  角色名称 <span className="text-rose-500">*</span>
                </Label>
                <Input
                  placeholder="如 财务管理员"
                  value={editing.roleName}
                  onChange={(e) => setEditing({ ...editing, roleName: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>
                  权限字符（创建后不可改） <span className="text-rose-500">*</span>
                </Label>
                <Input
                  disabled={!isNew}
                  placeholder="如 finance_admin"
                  className="font-mono"
                  value={editing.roleKey}
                  onChange={(e) => setEditing({ ...editing, roleKey: e.target.value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label>排序</Label>
                  <Input
                    type="number"
                    value={editing.roleSort ?? 0}
                    onChange={(e) => setEditing({ ...editing, roleSort: Number(e.target.value) })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label>数据权限</Label>
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                    value={editing.dataScope ?? 5}
                    onChange={(e) => setEditing({ ...editing, dataScope: Number(e.target.value) })}
                  >
                    {DATA_SCOPES.map((s) => (
                      <option key={s.value} value={s.value}>
                        {s.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="space-y-1.5">
                <Label>状态</Label>
                <select
                  className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                  value={editing.status}
                  onChange={(e) => setEditing({ ...editing, status: Number(e.target.value) })}
                >
                  <option value={1}>启用</option>
                  <option value={0}>禁用</option>
                </select>
              </div>
              <div className="space-y-1.5">
                <Label>备注</Label>
                <Input
                  value={editing.remark || ""}
                  onChange={(e) => setEditing({ ...editing, remark: e.target.value })}
                />
              </div>
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

      {/* 菜单授权：右侧抽屉（菜单树较宽，用大尺寸） */}
      <Sheet open={!!authTarget} onOpenChange={(open) => !open && setAuthTarget(null)}>
        <SheetContent className="sm:max-w-2xl">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>菜单授权 - {authTarget?.roleName}</SheetTitle>
            <SheetDescription>
              按目录/菜单/按钮三级勾选，全量替换保存（含按钮权限点）
            </SheetDescription>
          </SheetHeader>
          {/* min-h-0：flex 子项防溢出，确保菜单树内部滚动、底部保存按钮始终可见 */}
          <ScrollArea className="min-h-0 flex-1 rounded-lg border bg-slate-50/40 p-2">
            {menuRows.map(({ menu, depth }) => (
              <label
                key={menu.id}
                className="flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 text-sm hover:bg-slate-100/70"
                style={{ paddingLeft: 12 + depth * 20 }}
              >
                <input
                  type="checkbox"
                  className="h-4 w-4 accent-indigo-500"
                  checked={checkedMenuIds.includes(menu.id)}
                  onChange={() => toggleMenu(menu.id)}
                />
                <span className="font-medium text-slate-700">{menu.menuName}</span>
                <Badge variant="outline" className="text-[10px]">
                  {MENU_TYPE_LABEL[menu.menuType]}
                </Badge>
                {menu.perms && (
                  <span className="font-mono text-xs text-muted-foreground">{menu.perms}</span>
                )}
              </label>
            ))}
          </ScrollArea>
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setAuthTarget(null)}>
              取消
            </Button>
            <Button onClick={saveAuth} disabled={saving} className="min-w-24">
              {saving ? "保存中..." : "保存授权"}
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
  return <RoleAdminPage />;
}
