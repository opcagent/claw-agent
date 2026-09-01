"use client";

/**
 * 菜单管理：平台级菜单/按钮的增删改（仅平台管理员可见操作按钮）
 * + 菜单关联角色（租户管理员可维护本租户角色的关联）。
 * 关联关系与角色页的菜单授权共用 sys_role_menu，双向数据一致。
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Pencil, Plus, Trash2, Users } from "lucide-react";
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

const MENU_TYPE_LABEL: Record<string, string> = { M: "目录", C: "菜单", F: "按钮" };

/** 编辑草稿（新增时无 id） */
type MenuDraft = Omit<SysMenu, "id"> & { id?: number };

function emptyMenu(): MenuDraft {
  return { parentId: 0, menuName: "", menuType: "C", orderNum: 0, path: "", icon: "", perms: "", visible: 1, status: 1 };
}

function MenuAdminPage() {
  const hasPerm = useAuthStore((s) => s.hasPerm);
  const [menus, setMenus] = useState<SysMenu[]>([]);
  const [editing, setEditing] = useState<MenuDraft | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [saving, setSaving] = useState(false);

  // 关联角色对话框状态
  const [linkTarget, setLinkTarget] = useState<SysMenu | null>(null);
  const [tenantRoles, setTenantRoles] = useState<SysRole[]>([]);
  const [checkedRoleIds, setCheckedRoleIds] = useState<number[]>([]);

  const load = useCallback(async () => {
    try {
      const res = await api.get<SysMenu[]>("/api/adminMenu/list");
      setMenus(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** 菜单按 parentId 组装为扁平树行（带缩进层级） */
  const menuRows = useMemo(() => {
    const children = new Map<number, SysMenu[]>();
    for (const m of menus) {
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
  }, [menus]);

  /** 父菜单候选：排除自身（新增时为全部） */
  const parentOptions = useMemo(() => {
    if (!editing) return menus;
    return menus.filter((m) => m.id !== editing.id);
  }, [menus, editing]);

  async function save() {
    if (!editing) return;
    if (!editing.menuName) {
      toast.error("菜单名称必填");
      return;
    }
    setSaving(true);
    try {
      if (isNew) {
        await api.post("/api/adminMenu", editing);
      } else {
        await api.put(`/api/adminMenu/${editing.id}`, editing);
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

  async function remove(m: SysMenu) {
    if (!window.confirm(`删除菜单「${m.menuName}」？`)) return;
    try {
      await api.del(`/api/adminMenu/${m.id}`);
      toast.success("已删除");
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  /** 打开关联角色对话框：加载本租户角色 + 该菜单已关联角色 */
  async function openLink(m: SysMenu) {
    try {
      const [roles, linked] = await Promise.all([
        api.get<SysRole[]>("/api/adminRole/list"),
        api.get<number[]>(`/api/adminMenu/${m.id}/roles`),
      ]);
      setTenantRoles(roles.data || []);
      setCheckedRoleIds(linked.data || []);
      setLinkTarget(m);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载关联数据失败");
    }
  }

  function toggleRole(roleId: number) {
    setCheckedRoleIds((ids) =>
      ids.includes(roleId) ? ids.filter((i) => i !== roleId) : [...ids, roleId]
    );
  }

  async function saveLink() {
    if (!linkTarget) return;
    setSaving(true);
    try {
      await api.put(`/api/adminMenu/${linkTarget.id}/roles`, { roleIds: checkedRoleIds });
      toast.success("关联角色已保存");
      setLinkTarget(null);
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
              <h1 className="text-xl font-semibold text-slate-800">菜单权限</h1>
              <p className="text-sm text-slate-500">
                维护平台菜单与权限点，并管理菜单与角色的关联
              </p>
            </div>
            {/* 菜单为平台级数据：新增仅平台管理员（后端同步拦截） */}
            {hasPerm("system:menu:add") && (
              <Button
                className="gap-1 bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600"
                onClick={() => {
                  setEditing(emptyMenu());
                  setIsNew(true);
                }}
              >
                <Plus className="h-4 w-4" /> 新增菜单
              </Button>
            )}
          </div>

          {/* 菜单树形表格卡片 */}
          <div className="rounded-2xl border border-slate-200/70 bg-white shadow-sm">
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/60">
                  <TableHead>菜单名称</TableHead>
                  <TableHead>类型</TableHead>
                  <TableHead>权限标识</TableHead>
                  <TableHead>排序</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {menuRows.map(({ menu, depth }) => (
                  <TableRow key={menu.id}>
                    <TableCell>
                      <span className="font-medium" style={{ paddingLeft: depth * 20 }}>
                        {menu.menuName}
                      </span>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline">{MENU_TYPE_LABEL[menu.menuType]}</Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs">{menu.perms || "-"}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{menu.orderNum}</TableCell>
                    <TableCell>
                      <Badge variant={menu.status === 1 ? "default" : "secondary"}>
                        {menu.status === 1 ? "启用" : "禁用"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button variant="ghost" size="icon" title="关联角色" onClick={() => openLink(menu)}>
                        <Users className="h-4 w-4 text-indigo-500" />
                      </Button>
                      {hasPerm("system:menu:edit") && (
                        <Button
                          variant="ghost"
                          size="icon"
                          title="编辑"
                          onClick={() => {
                            setEditing({ ...menu });
                            setIsNew(false);
                          }}
                        >
                          <Pencil className="h-4 w-4 text-slate-500" />
                        </Button>
                      )}
                      {hasPerm("system:menu:remove") && (
                        <Button variant="ghost" size="icon" title="删除" onClick={() => remove(menu)}>
                          <Trash2 className="h-4 w-4 text-rose-500" />
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {menuRows.length === 0 && (
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

      {/* 菜单编辑：右侧抽屉（新增/编辑共用，表单字段较多故用大尺寸 Sheet） */}
      <Sheet open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <SheetContent className="sm:max-w-2xl">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>{isNew ? "新增菜单" : "编辑菜单"}</SheetTitle>
            <SheetDescription>
              目录/菜单用于前端导航展示，按钮类型仅承载权限点标识。
            </SheetDescription>
          </SheetHeader>
          {editing && (
            <div className="flex-1 space-y-5 overflow-y-auto px-1 py-2">
              {/* 基础信息 */}
              <section className="space-y-4">
                <h3 className="text-xs font-semibold tracking-wide text-slate-400 uppercase">基础信息</h3>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label>
                      菜单名称 <span className="text-rose-500">*</span>
                    </Label>
                    <Input
                      placeholder="如 用户管理"
                      value={editing.menuName}
                      onChange={(e) => setEditing({ ...editing, menuName: e.target.value })}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label>类型</Label>
                    <select
                      className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                      value={editing.menuType}
                      onChange={(e) => setEditing({ ...editing, menuType: e.target.value as SysMenu["menuType"] })}
                    >
                      <option value="M">目录</option>
                      <option value="C">菜单</option>
                      <option value="F">按钮</option>
                    </select>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label>父菜单</Label>
                    <select
                      className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                      value={editing.parentId}
                      onChange={(e) => setEditing({ ...editing, parentId: Number(e.target.value) })}
                    >
                      <option value={0}>根节点</option>
                      {parentOptions.map((m) => (
                        <option key={m.id} value={m.id}>
                          {m.menuName}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-1.5">
                    <Label>排序</Label>
                    <Input
                      type="number"
                      value={editing.orderNum ?? 0}
                      onChange={(e) => setEditing({ ...editing, orderNum: Number(e.target.value) })}
                    />
                  </div>
                </div>
              </section>

              {/* 路由与权限 */}
              <section className="space-y-4 border-t pt-4">
                <h3 className="text-xs font-semibold tracking-wide text-slate-400 uppercase">路由与权限</h3>
                <div className="space-y-1.5">
                  <Label>路由/组件路径</Label>
                  <Input
                    placeholder="如 /system/user"
                    value={editing.path || ""}
                    onChange={(e) => setEditing({ ...editing, path: e.target.value })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label>权限标识</Label>
                  <Input
                    placeholder="如 system:user:add"
                    className="font-mono"
                    value={editing.perms || ""}
                    onChange={(e) => setEditing({ ...editing, perms: e.target.value })}
                  />
                  <p className="text-xs text-muted-foreground">按钮级鉴权依据，后端 @RequirePerm 与之对应</p>
                </div>
              </section>

              {/* 展示与状态 */}
              <section className="space-y-4 border-t pt-4">
                <h3 className="text-xs font-semibold tracking-wide text-slate-400 uppercase">展示与状态</h3>
                <div className="grid grid-cols-3 gap-4">
                  <div className="space-y-1.5">
                    <Label>图标</Label>
                    <Input
                      placeholder="lucide 图标名"
                      value={editing.icon || ""}
                      onChange={(e) => setEditing({ ...editing, icon: e.target.value })}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label>显示</Label>
                    <select
                      className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                      value={editing.visible}
                      onChange={(e) => setEditing({ ...editing, visible: Number(e.target.value) })}
                    >
                      <option value={1}>显示</option>
                      <option value={0}>隐藏</option>
                    </select>
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

      {/* 关联角色：右侧抽屉 */}
      <Sheet open={!!linkTarget} onOpenChange={(open) => !open && setLinkTarget(null)}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>关联角色 - {linkTarget?.menuName}</SheetTitle>
            <SheetDescription>勾选拥有该菜单访问权的租户角色</SheetDescription>
          </SheetHeader>
          <ScrollArea className="flex-1 rounded-lg border bg-slate-50/40 p-2">
            {tenantRoles.map((r) => (
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
              </label>
            ))}
            {tenantRoles.length === 0 && (
              <p className="py-6 text-center text-sm text-muted-foreground">本租户暂无角色</p>
            )}
          </ScrollArea>
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setLinkTarget(null)}>
              取消
            </Button>
            <Button onClick={saveLink} disabled={saving} className="min-w-24">
              {saving ? "保存中..." : "保存关联"}
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
  return <MenuAdminPage />;
}
