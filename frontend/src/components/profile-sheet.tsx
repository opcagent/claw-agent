"use client";

/**
 * 个人信息详情抽屉：本人基础资料 + 最近一次成功登录 + 最近登录记录列表。
 * 数据全部来自服务端（/api/auth/profile 与 /api/auth/login-logs），
 * 查询对象由 JWT 定位，只能看到自己的信息；昵称/联系方式支持本人自助编辑。
 */
import { useEffect, useState } from "react";
import { Clock, MapPin, Pencil } from "lucide-react";
import { toast } from "sonner";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { api } from "@/lib/api";
import type { LoginLog, ProfileInfo } from "@/lib/types";

/** 后端 LocalDateTime 序列化为 ISO 串，统一转成「yyyy-MM-dd HH:mm:ss」展示 */
function fmtTime(t: string | null | undefined): string {
  if (!t) return "-";
  return t.replace("T", " ").slice(0, 19);
}

export default function ProfileSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [profile, setProfile] = useState<ProfileInfo | null>(null);
  const [logs, setLogs] = useState<LoginLog[]>([]);
  const [loading, setLoading] = useState(false);

  // 自助编辑态：表单只覆盖昵称/手机/邮箱/性别，其余字段（租户/登录记录）不可改
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editForm, setEditForm] = useState({
    nickname: "",
    phone: "",
    email: "",
    gender: 0,
  });

  // 每次打开拉最新（登录记录随会话变化，缓存无意义）
  useEffect(() => {
    if (!open) return;
    setLoading(true);
    Promise.all([
      api.get<ProfileInfo>("/api/auth/profile"),
      api.get<LoginLog[]>("/api/auth/login-logs?limit=10"),
    ])
      .then(([p, l]) => {
        setProfile(p.data || null);
        setLogs(l.data || []);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
    // 关闭抽屉时退出编辑态，避免下次打开残留旧表单
    return () => setEditing(false);
  }, [open]);

  /** 进入编辑态：用当前资料回填表单 */
  function startEdit(p: ProfileInfo) {
    setEditForm({
      nickname: p.nickname || "",
      phone: p.phone || "",
      email: p.email || "",
      gender: p.gender ?? 0,
    });
    setEditing(true);
  }

  /** 提交自助修改（后端校验格式，失败透提示） */
  async function saveProfile() {
    setSaving(true);
    try {
      await api.put("/api/auth/profile", {
        nickname: editForm.nickname || null,
        phone: editForm.phone || null,
        email: editForm.email || null,
        gender: editForm.gender,
      });
      toast.success("个人资料已更新");
      setEditing(false);
      const p = await api.get<ProfileInfo>("/api/auth/profile");
      setProfile(p.data || null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-xl">
        <SheetHeader className="border-b pb-4">
          <SheetTitle>个人信息</SheetTitle>
          <SheetDescription>账号资料与最近登录情况</SheetDescription>
        </SheetHeader>

        <div className="flex-1 space-y-5 overflow-y-auto py-2">
          {loading && (
            <p className="py-8 text-center text-sm text-muted-foreground">
              加载中...
            </p>
          )}

          {!loading && profile && editing && (
            <div className="space-y-5">
              <div className="space-y-1.5">
                <Label>昵称</Label>
                <Input
                  value={editForm.nickname}
                  onChange={(e) => setEditForm({ ...editForm, nickname: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>手机号码</Label>
                <Input
                  placeholder="11 位，1 开头（留空清除）"
                  value={editForm.phone}
                  onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>邮箱</Label>
                <Input
                  placeholder="user@example.com（留空清除）"
                  value={editForm.email}
                  onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>性别</Label>
                <select
                  className="h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  value={editForm.gender}
                  onChange={(e) => setEditForm({ ...editForm, gender: Number(e.target.value) })}
                >
                  <option value={0}>未知</option>
                  <option value={1}>男</option>
                  <option value={2}>女</option>
                </select>
              </div>
              <SheetFooter className="border-t pt-4">
                <Button variant="outline" disabled={saving} onClick={() => setEditing(false)}>
                  取消
                </Button>
                <Button onClick={saveProfile} disabled={saving} className="min-w-24">
                  {saving ? "保存中..." : "保存"}
                </Button>
              </SheetFooter>
            </div>
          )}

          {!loading && profile && !editing && (
            <>
              {/* 基础资料 */}
              <div className="flex items-center gap-4">
                <Avatar className="h-14 w-14">
                  <AvatarFallback className="bg-gradient-to-br from-indigo-500 to-violet-500 text-lg text-white">
                    {(profile.nickname || profile.username).slice(0, 1).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <div>
                  <p className="text-base font-semibold text-slate-800">
                    {profile.nickname || profile.username}
                  </p>
                  <p className="font-mono text-xs text-muted-foreground">
                    @{profile.username}
                  </p>
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {(profile.roleNames.length > 0
                      ? profile.roleNames
                      : profile.roleKeys
                    ).map((r) => (
                      <Badge key={r} variant="secondary">
                        {r}
                      </Badge>
                    ))}
                  </div>
                </div>
              </div>

              {/* 账号信息（含联系方式：性别/手机/邮箱可自助编辑） */}
              <div className="rounded-xl border bg-slate-50/50 p-4">
                <div className="mb-3 flex items-center justify-between">
                  <p className="text-sm font-medium text-slate-700">账号信息</p>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 gap-1 text-xs text-indigo-500"
                    onClick={() => startEdit(profile)}
                  >
                    <Pencil className="h-3 w-3" /> 编辑资料
                  </Button>
                </div>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <p className="text-xs text-muted-foreground">性别</p>
                    <p className="mt-0.5 font-medium">
                      {profile.gender === 1 ? "男" : profile.gender === 2 ? "女" : "未知"}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">手机号码</p>
                    <p className="mt-0.5 font-medium">{profile.phone || "-"}</p>
                  </div>
                  <div className="col-span-2">
                    <p className="text-xs text-muted-foreground">邮箱</p>
                    <p className="mt-0.5 font-medium">{profile.email || "-"}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">所属租户</p>
                    {/* 展示租户名称而非 ID，名称缺失时降级显示 ID */}
                    <p className="mt-0.5 font-medium">
                      {profile.tenantName || (profile.tenantId != null ? `#${profile.tenantId}` : "-")}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">账号创建时间</p>
                    <p className="mt-0.5 font-medium">{fmtTime(profile.createTime)}</p>
                  </div>
                </div>
              </div>

              {/* 上次登录 */}
              <div className="rounded-xl border border-indigo-100 bg-indigo-50/50 p-4">
                <p className="text-xs font-medium text-indigo-500">上次登录</p>
                <div className="mt-2 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-sm text-slate-700">
                  <span className="flex items-center gap-1.5">
                    <Clock className="h-3.5 w-3.5 text-indigo-400" />
                    {fmtTime(profile.lastLoginTime)}
                  </span>
                  <span className="flex items-center gap-1.5">
                    <MapPin className="h-3.5 w-3.5 text-indigo-400" />
                    {profile.lastLoginIp || "-"}
                  </span>
                </div>
              </div>

              {/* 最近登录记录 */}
              <div>
                <p className="mb-2 text-sm font-medium text-slate-700">
                  最近登录记录
                </p>
                {logs.length === 0 ? (
                  <p className="py-4 text-center text-sm text-muted-foreground">
                    暂无登录记录
                  </p>
                ) : (
                  <ul className="divide-y rounded-xl border">
                    {logs.map((log) => (
                      <li
                        key={log.id}
                        className="flex items-center justify-between gap-3 px-3.5 py-2.5 text-sm"
                      >
                        <div className="min-w-0">
                          <p className="truncate font-mono text-xs text-slate-600">
                            {fmtTime(log.loginTime)}
                          </p>
                          <p className="mt-0.5 truncate text-xs text-muted-foreground">
                            {log.ip || "-"}
                            {log.msg && log.status === 0 ? ` · ${log.msg}` : ""}
                          </p>
                        </div>
                        {log.status === 1 ? (
                          <Badge className="bg-emerald-500">成功</Badge>
                        ) : (
                          <Badge variant="destructive">失败</Badge>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
