"use client";

/**
 * 字典管理：字典类型 + 字典数据两级维护（若依风格）。
 * 作用域两档：PLATFORM 平台公共字典（仅平台管理员）、TENANT 租户字典（租户管理员及以上）。
 * 读取侧平台 + 本租户合并且租户覆盖平台；本页面维护的是单一作用域的原始数据。
 */
import { useCallback, useEffect, useState } from "react";
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
import type { DictData, DictType } from "@/lib/types";

type Scope = "TENANT" | "PLATFORM";

/** css_class 标签色预览映射（与字典数据 css_class 字段约定） */
const CSS_CLASS_COLOR: Record<string, string> = {
  success: "bg-emerald-500",
  danger: "bg-rose-500",
  warning: "bg-amber-500",
  info: "bg-sky-500",
  primary: "bg-indigo-500",
};

function DictPage() {
  const isAdmin = useAuthStore((s) => s.isAdmin)();
  // 按钮权限点控制（与后端 @PreAuthorize 同点：system:dict:add/edit/remove），仅展示层，后端仍强制鉴权
  const hasPerm = useAuthStore((s) => s.hasPerm);
  const canAdd = hasPerm("system:dict:add");
  const canEdit = hasPerm("system:dict:edit");
  const canRemove = hasPerm("system:dict:remove");

  const scopeOptions: Scope[] = ["TENANT"];
  if (isAdmin) scopeOptions.push("PLATFORM");

  const [scope, setScope] = useState<Scope>("TENANT");
  const [types, setTypes] = useState<DictType[]>([]);
  const [selectedType, setSelectedType] = useState<DictType | null>(null);
  const [items, setItems] = useState<DictData[]>([]);
  const [editType, setEditType] = useState<DictType | null>(null);
  const [editItem, setEditItem] = useState<DictData | null>(null);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(false);

  /* ---------------- 数据加载 ---------------- */

  const loadTypes = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<DictType[]>(`/api/dict/types?scope=${scope}`);
      setTypes(res.data || []);
      return res.data || [];
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载字典类型失败");
      return [];
    } finally {
      setLoading(false);
    }
  }, [scope]);

  const loadItems = useCallback(
    async (dictType: string) => {
      try {
        const res = await api.get<DictData[]>(
          `/api/dict/items?scope=${scope}&dictType=${encodeURIComponent(dictType)}`
        );
        setItems(res.data || []);
      } catch (e) {
        toast.error(e instanceof Error ? e.message : "加载字典数据失败");
      }
    },
    [scope]
  );

  useEffect(() => {
    // 作用域切换：重载类型并默认选中第一个（联动右侧数据表）
    setSelectedType(null);
    setItems([]);
    loadTypes().then((list) => {
      if (list.length > 0) {
        setSelectedType(list[0]);
        loadItems(list[0].dictType);
      }
    });
  }, [loadTypes, loadItems]);

  /* ---------------- 字典类型操作 ---------------- */

  async function saveType() {
    if (!editType) return;
    if (!editType.dictName.trim() || !editType.dictType.trim()) {
      toast.error("字典名称与字典类型不能为空");
      return;
    }
    setSaving(true);
    try {
      // 有 id 走修改（PUT，权限点 dict:edit），否则新增（POST，dict:add）
      if (editType.id) {
        await api.put(`/api/dict/type?scope=${scope}`, editType);
      } else {
        await api.post(`/api/dict/type?scope=${scope}`, editType);
      }
      toast.success("已保存");
      setEditType(null);
      loadTypes();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function deleteType(t: DictType) {
    if (!window.confirm(`删除字典「${t.dictName}」？名下字典数据将一并删除`)) return;
    try {
      await api.del(`/api/dict/type/${t.id}?scope=${scope}`);
      toast.success("已删除");
      if (selectedType?.id === t.id) {
        setSelectedType(null);
        setItems([]);
      }
      loadTypes();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  /* ---------------- 字典数据操作 ---------------- */

  async function saveItem() {
    if (!editItem || !selectedType) return;
    if (!editItem.dictLabel.trim() || !editItem.dictValue.trim()) {
      toast.error("字典标签与键值不能为空");
      return;
    }
    setSaving(true);
    try {
      const payload = { ...editItem, dictType: selectedType.dictType };
      if (editItem.id) {
        await api.put(`/api/dict/item?scope=${scope}`, payload);
      } else {
        await api.post(`/api/dict/item?scope=${scope}`, payload);
      }
      toast.success("已保存");
      setEditItem(null);
      loadItems(selectedType.dictType);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function deleteItem(d: DictData) {
    if (!window.confirm(`删除字典数据「${d.dictLabel}」？`)) return;
    try {
      await api.del(`/api/dict/item/${d.id}?scope=${scope}`);
      toast.success("已删除");
      if (selectedType) loadItems(selectedType.dictType);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  /* ---------------- 渲染 ---------------- */

  return (
    <AppShell>
      <div className="h-full overflow-auto p-6">
        <div className="space-y-4">
          {/* 页头 + 作用域切换 */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-semibold text-slate-800">数据字典</h1>
              <p className="text-sm text-slate-500">
                下拉/标签的数据源；读取时平台与租户字典合并（租户覆盖平台）
              </p>
            </div>
            <div className="flex rounded-full border border-slate-200 bg-white p-1 shadow-sm">
              {scopeOptions.map((s) => (
                <button
                  key={s}
                  onClick={() => setScope(s)}
                  className={
                    "rounded-full px-4 py-1 text-sm transition-colors " +
                    (scope === s ? "bg-slate-800 text-white" : "text-slate-600 hover:bg-slate-100")
                  }
                >
                  {s === "TENANT" ? "租户字典" : "平台字典"}
                </button>
              ))}
            </div>
          </div>

          {/* 加载状态提示 */}
          {loading && (
            <div className="flex items-center justify-center py-12">
              <div className="text-center">
                <div className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-slate-200 border-t-slate-600"></div>
                <p className="mt-3 text-sm text-slate-500">加载中...</p>
              </div>
            </div>
          )}

          {!loading && (
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-5">
            {/* 左侧：字典类型 */}
            <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm lg:col-span-2">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="font-medium text-slate-700">字典类型</h2>
                {canAdd && (
                  <Button
                    size="sm"
                    variant="outline"
                    className="gap-1"
                    onClick={() => setEditType({ dictName: "", dictType: "", status: 1 })}
                  >
                    <Plus className="h-3.5 w-3.5" /> 新增
                  </Button>
                )}
              </div>
              <Table>
                <TableHeader>
                  <TableRow className="bg-slate-50/60">
                    <TableHead>名称 / 类型</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {types.map((t) => (
                    <TableRow
                      key={t.id}
                      className={
                        "cursor-pointer " +
                        (selectedType?.id === t.id ? "bg-indigo-50/70" : "hover:bg-slate-50")
                      }
                      onClick={() => {
                        setSelectedType(t);
                        loadItems(t.dictType);
                      }}
                    >
                      <TableCell>
                        <p className="text-sm">{t.dictName}</p>
                        <p className="font-mono text-xs text-muted-foreground">{t.dictType}</p>
                      </TableCell>
                      <TableCell>
                        {t.status === 1 ? (
                          <Badge className="bg-emerald-500">启用</Badge>
                        ) : (
                          <Badge variant="secondary">禁用</Badge>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        {canEdit && (
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={(e) => {
                              e.stopPropagation();
                              setEditType({ ...t });
                            }}
                          >
                            <Pencil className="h-4 w-4 text-slate-500" />
                          </Button>
                        )}
                        {canRemove && (
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={(e) => {
                              e.stopPropagation();
                              deleteType(t);
                            }}
                          >
                            <Trash2 className="h-4 w-4 text-rose-500" />
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                  {types.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={3} className="py-6 text-center text-muted-foreground">
                        该作用域暂无字典类型
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </section>

            {/* 右侧：选中类型的字典数据 */}
            <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm lg:col-span-3">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="font-medium text-slate-700">
                  字典数据
                  {selectedType && (
                    <span className="ml-2 font-mono text-xs font-normal text-muted-foreground">
                      {selectedType.dictType}
                    </span>
                  )}
                </h2>
                <Button
                  size="sm"
                  variant="outline"
                  className="gap-1"
                  disabled={!selectedType}
                  hidden={!canAdd}
                  onClick={() =>
                    selectedType &&
                    setEditItem({
                      dictType: selectedType.dictType,
                      dictLabel: "",
                      dictValue: "",
                      dictSort: items.length + 1,
                      defaultFlag: 0,
                      status: 1,
                    })
                  }
                >
                  <Plus className="h-3.5 w-3.5" /> 新增
                </Button>
              </div>
              {!selectedType ? (
                <p className="py-10 text-center text-sm text-muted-foreground">
                  点击左侧字典类型查看其数据
                </p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow className="bg-slate-50/60">
                      <TableHead>排序</TableHead>
                      <TableHead>标签</TableHead>
                      <TableHead>键值</TableHead>
                      <TableHead>样式</TableHead>
                      <TableHead>默认</TableHead>
                      <TableHead>状态</TableHead>
                      <TableHead className="text-right">操作</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((d) => (
                      <TableRow key={d.id}>
                        <TableCell className="text-muted-foreground">{d.dictSort}</TableCell>
                        <TableCell>{d.dictLabel}</TableCell>
                        <TableCell className="font-mono text-xs">{d.dictValue}</TableCell>
                        <TableCell>
                          {d.cssClass ? (
                            <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                              <span
                                className={
                                  "h-2.5 w-2.5 rounded-full " +
                                  (CSS_CLASS_COLOR[d.cssClass] || "bg-slate-300")
                                }
                              />
                              {d.cssClass}
                            </span>
                          ) : (
                            "-"
                          )}
                        </TableCell>
                        <TableCell>{d.defaultFlag === 1 ? "✓" : "-"}</TableCell>
                        <TableCell>
                          {d.status === 1 ? (
                            <Badge className="bg-emerald-500">启用</Badge>
                          ) : (
                            <Badge variant="secondary">禁用</Badge>
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          {canEdit && (
                            <Button variant="ghost" size="icon" onClick={() => setEditItem({ ...d })}>
                              <Pencil className="h-4 w-4 text-slate-500" />
                            </Button>
                          )}
                          {canRemove && (
                            <Button variant="ghost" size="icon" onClick={() => deleteItem(d)}>
                              <Trash2 className="h-4 w-4 text-rose-500" />
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                    {items.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={7} className="py-6 text-center text-muted-foreground">
                          该字典类型下暂无数据
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              )}
            </section>
          </div>
          )}
        </div>
      </div>

      {/* 字典类型编辑：右侧抽屉 */}
      <Sheet open={!!editType} onOpenChange={(open) => !open && setEditType(null)}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>{editType?.id ? "编辑字典类型" : "新增字典类型"}</SheetTitle>
            <SheetDescription>
              字典类型编码保存后不可变更（字典数据按它关联）
            </SheetDescription>
          </SheetHeader>
          {editType && (
            <div className="flex-1 space-y-5 overflow-y-auto py-2">
              <div className="space-y-1.5">
                <Label>
                  字典名称 <span className="text-rose-500">*</span>
                </Label>
                <Input
                  placeholder="如 通用状态"
                  value={editType.dictName}
                  onChange={(e) => setEditType({ ...editType, dictName: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>
                  字典类型编码 <span className="text-rose-500">*</span>
                </Label>
                <Input
                  placeholder="如 sys_normal_disable（小写下划线）"
                  className="font-mono"
                  disabled={!!editType.id}
                  value={editType.dictType}
                  onChange={(e) => setEditType({ ...editType, dictType: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>状态</Label>
                <select
                  className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                  value={editType.status}
                  onChange={(e) => setEditType({ ...editType, status: Number(e.target.value) })}
                >
                  <option value={1}>启用</option>
                  <option value={0}>禁用</option>
                </select>
              </div>
              <div className="space-y-1.5">
                <Label>备注</Label>
                <Input
                  value={editType.remark || ""}
                  onChange={(e) => setEditType({ ...editType, remark: e.target.value })}
                />
              </div>
            </div>
          )}
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setEditType(null)}>
              取消
            </Button>
            <Button onClick={saveType} disabled={saving} className="min-w-24">
              {saving ? "保存中..." : "保存"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>

      {/* 字典数据编辑：右侧抽屉 */}
      <Sheet open={!!editItem} onOpenChange={(open) => !open && setEditItem(null)}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>{editItem?.id ? "编辑字典数据" : "新增字典数据"}</SheetTitle>
            <SheetDescription>
              归属字典类型：{selectedType?.dictType}（{selectedType?.dictName}）
            </SheetDescription>
          </SheetHeader>
          {editItem && (
            <div className="flex-1 space-y-5 overflow-y-auto py-2">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label>
                    字典标签 <span className="text-rose-500">*</span>
                  </Label>
                  <Input
                    placeholder="展示文案，如 启用"
                    value={editItem.dictLabel}
                    onChange={(e) => setEditItem({ ...editItem, dictLabel: e.target.value })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label>
                    字典键值 <span className="text-rose-500">*</span>
                  </Label>
                  <Input
                    placeholder="程序使用的值，如 1"
                    className="font-mono"
                    value={editItem.dictValue}
                    onChange={(e) => setEditItem({ ...editItem, dictValue: e.target.value })}
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label>显示顺序</Label>
                  <Input
                    type="number"
                    value={editItem.dictSort}
                    onChange={(e) => setEditItem({ ...editItem, dictSort: Number(e.target.value) })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label>样式（标签色）</Label>
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                    value={editItem.cssClass || ""}
                    onChange={(e) =>
                      setEditItem({ ...editItem, cssClass: e.target.value || null })
                    }
                  >
                    <option value="">无</option>
                    <option value="success">success（绿）</option>
                    <option value="danger">danger（红）</option>
                    <option value="warning">warning（黄）</option>
                    <option value="info">info（蓝）</option>
                    <option value="primary">primary（紫）</option>
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label>状态</Label>
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                    value={editItem.status}
                    onChange={(e) => setEditItem({ ...editItem, status: Number(e.target.value) })}
                  >
                    <option value={1}>启用</option>
                    <option value={0}>禁用</option>
                  </select>
                </div>
                <label className="flex items-end gap-2 pb-2 text-sm">
                  <input
                    type="checkbox"
                    className="h-4 w-4 accent-indigo-500"
                    checked={editItem.defaultFlag === 1}
                    onChange={(e) => setEditItem({ ...editItem, defaultFlag: e.target.checked ? 1 : 0 })}
                  />
                  设为默认值
                </label>
              </div>
              <div className="space-y-1.5">
                <Label>备注</Label>
                <Input
                  value={editItem.remark || ""}
                  onChange={(e) => setEditItem({ ...editItem, remark: e.target.value })}
                />
              </div>
            </div>
          )}
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setEditItem(null)}>
              取消
            </Button>
            <Button onClick={saveItem} disabled={saving} className="min-w-24">
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
  return <DictPage />;
}
