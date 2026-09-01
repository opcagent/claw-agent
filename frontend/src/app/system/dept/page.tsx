"use client";

/**
 * 部门管理：本租户部门树（扁平缩进渲染）/ 新增 / 编辑 / 删除。
 * 后端返回扁平列表，前端按 parentId 组装层级。
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Pencil, Plus, Trash2 } from "lucide-react";
import AppShell from "@/components/app-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
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
import type { SysDept } from "@/lib/types";

function emptyDept(): SysDept {
  return { parentId: 0, deptName: "", orderNum: 0, leader: "", status: 1 };
}

function DeptAdminPage() {
  const hasPerm = useAuthStore((s) => s.hasPerm);
  const [depts, setDepts] = useState<SysDept[]>([]);
  const [editing, setEditing] = useState<SysDept | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await api.get<SysDept[]>("/api/adminDept/list");
      setDepts(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /** 按父子关系展平为带深度的行序（父在前子紧随） */
  const rows = useMemo(() => {
    const children = new Map<number, SysDept[]>();
    for (const d of depts) {
      const list = children.get(d.parentId) ?? [];
      list.push(d);
      children.set(d.parentId, list);
    }
    const out: { dept: SysDept; depth: number }[] = [];
    const walk = (parentId: number, depth: number) => {
      for (const d of children.get(parentId) ?? []) {
        out.push({ dept: d, depth });
        walk(d.id ?? 0, depth + 1);
      }
    };
    walk(0, 0);
    return out;
  }, [depts]);

  async function save() {
    if (!editing) return;
    if (!editing.deptName) {
      toast.error("部门名称必填");
      return;
    }
    setSaving(true);
    try {
      if (isNew) {
        await api.post("/api/adminDept", editing);
      } else {
        await api.put(`/api/adminDept/${editing.id}`, editing);
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

  async function remove(d: SysDept) {
    if (!window.confirm(`删除部门「${d.deptName}」？`)) return;
    try {
      await api.del(`/api/adminDept/${d.id}`);
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
              <h1 className="text-xl font-semibold text-slate-800">组织架构</h1>
              <p className="text-sm text-slate-500">维护本租户组织架构（树形）</p>
            </div>
            {/* 新增按钮按权限点显隐（后端同步拦截） */}
            {hasPerm("system:dept:add") && (
              <Button
                className="gap-1 bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600"
                onClick={() => {
                  setEditing(emptyDept());
                  setIsNew(true);
                }}
              >
                <Plus className="h-4 w-4" /> 新增部门
              </Button>
            )}
          </div>

          {/* 部门表格卡片 */}
          <div className="rounded-2xl border border-slate-200/70 bg-white shadow-sm">
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/60">
                  <TableHead>部门名称</TableHead>
                  <TableHead>排序</TableHead>
                  <TableHead>负责人</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map(({ dept, depth }) => (
                  <TableRow key={dept.id}>
                    <TableCell className="font-medium">
                      <span style={{ paddingLeft: depth * 20 }}>
                        {depth > 0 && <span className="mr-1 text-slate-300">└</span>}
                        {dept.deptName}
                      </span>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{dept.orderNum ?? 0}</TableCell>
                    <TableCell>{dept.leader || "-"}</TableCell>
                    <TableCell>
                      <Badge variant={dept.status === 1 ? "default" : "secondary"}>
                        {dept.status === 1 ? "启用" : "禁用"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      {hasPerm("system:dept:edit") && (
                        <Button
                          variant="ghost"
                          size="icon"
                          title="编辑"
                          onClick={() => {
                            setEditing({ ...dept });
                            setIsNew(false);
                          }}
                        >
                          <Pencil className="h-4 w-4 text-slate-500" />
                        </Button>
                      )}
                      {hasPerm("system:dept:remove") && (
                        <Button variant="ghost" size="icon" title="删除" onClick={() => remove(dept)}>
                          <Trash2 className="h-4 w-4 text-rose-500" />
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                      暂无数据
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      </div>

      {/* 部门编辑：右侧抽屉 */}
      <Sheet open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>{isNew ? "新增部门" : "编辑部门"}</SheetTitle>
            <SheetDescription>部门为租户内组织架构，支持多级树形嵌套</SheetDescription>
          </SheetHeader>
          {editing && (
            <div className="flex-1 space-y-5 overflow-y-auto py-2">
              <div className="space-y-1.5">
                <Label>上级部门</Label>
                <select
                  className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                  value={editing.parentId}
                  onChange={(e) => setEditing({ ...editing, parentId: Number(e.target.value) })}
                >
                  <option value={0}>（根部门）</option>
                  {depts
                    .filter((d) => d.id !== editing.id)
                    .map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.deptName}
                      </option>
                    ))}
                </select>
              </div>
              <div className="space-y-1.5">
                <Label>
                  部门名称 <span className="text-rose-500">*</span>
                </Label>
                <Input
                  placeholder="如 研发部"
                  value={editing.deptName}
                  onChange={(e) => setEditing({ ...editing, deptName: e.target.value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label>排序</Label>
                  <Input
                    type="number"
                    value={editing.orderNum ?? 0}
                    onChange={(e) => setEditing({ ...editing, orderNum: Number(e.target.value) })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label>负责人（用户名）</Label>
                  <Input
                    value={editing.leader || ""}
                    onChange={(e) => setEditing({ ...editing, leader: e.target.value })}
                  />
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
  const { ready } = useAuthGuard({ requireTenantAdmin: true });
  if (!ready) return null;
  return <DeptAdminPage />;
}
