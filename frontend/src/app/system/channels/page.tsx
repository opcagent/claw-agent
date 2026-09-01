"use client";

/**
 * 渠道管理：管理用户与外部渠道（微信/Slack/Telegram 等）的绑定关系。
 * 支持单聊和群聊两种场景：
 * - 单聊：channelGroupId 为 NULL，会话归属于用户个人
 * - 群聊：channelGroupId 有值，会话归属于群组，群内成员共享上下文
 */
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";
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
import type { UserChannel } from "@/lib/types";

/** 渠道类型配置（图标色 + 显示名） */
const CHANNEL_TYPE_CONFIG: Record<string, { label: string; color: string }> = {
  wechat: { label: "微信", color: "bg-green-500" },
  slack: { label: "Slack", color: "bg-purple-500" },
  telegram: { label: "Telegram", color: "bg-blue-500" },
  web: { label: "Web", color: "bg-slate-500" },
};

/** 群角色显示 */
const GROUP_ROLE_LABEL: Record<string, string> = {
  owner: "群主",
  admin: "管理员",
  member: "成员",
};

function ChannelPage() {
  const hasPerm = useAuthStore((s) => s.hasPerm);
  const canAdd = hasPerm("system:channel:add");
  const canEdit = hasPerm("system:channel:edit");
  const canRemove = hasPerm("system:channel:remove");

  const [channels, setChannels] = useState<UserChannel[]>([]);
  const [editChannel, setEditChannel] = useState<UserChannel | null>(null);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(false);

  /* ---------------- 数据加载 ---------------- */

  const loadChannels = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<UserChannel[]>("/api/channel/list");
      setChannels(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载渠道列表失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadChannels();
  }, [loadChannels]);

  /* ---------------- 渠道操作 ---------------- */

  async function saveChannel() {
    if (!editChannel) return;
    if (!editChannel.channelType || !editChannel.channelUserId) {
      toast.error("渠道类型和渠道用户 ID 不能为空");
      return;
    }
    setSaving(true);
    try {
      if (editChannel.id) {
        await api.put(`/api/channel/${editChannel.id}`, editChannel);
      } else {
        await api.post("/api/channel", editChannel);
      }
      toast.success("已保存");
      setEditChannel(null);
      loadChannels();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function deleteChannel(ch: UserChannel) {
    if (!ch.id) return;
    const name = ch.channelUsername || ch.channelUserId;
    if (!window.confirm(`确定解绑渠道「${name}」？`)) return;
    try {
      await api.del(`/api/channel/${ch.id}`);
      toast.success("已解绑");
      loadChannels();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "解绑失败");
    }
  }

  async function syncMembers(ch: UserChannel) {
    if (!ch.id) return;
    try {
      await api.post(`/api/channel/${ch.id}/sync-members`, {});
      toast.success("群组成员同步请求已发送");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "同步失败");
    }
  }

  /* ---------------- 渲染 ---------------- */

  function getChannelTypeLabel(type: string) {
    return CHANNEL_TYPE_CONFIG[type]?.label || type;
  }

  function getChannelTypeColor(type: string) {
    return CHANNEL_TYPE_CONFIG[type]?.color || "bg-slate-400";
  }

  return (
    <AppShell>
      <div className="h-full overflow-auto p-6">
        <div className="space-y-4">
          {/* 页头 */}
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-semibold text-slate-800">渠道管理</h1>
              <p className="text-sm text-slate-500">
                管理用户与外部渠道（微信/Slack/Telegram 等）的绑定关系，支持单聊和群聊
              </p>
            </div>
            {canAdd && (
              <Button
                size="sm"
                className="gap-1"
                onClick={() =>
                  setEditChannel({
                    channelType: "wechat",
                    channelUserId: "",
                    channelUsername: "",
                    channelGroupId: "",
                    channelGroupName: "",
                    groupRole: "member",
                    status: 1,
                  })
                }
              >
                <Plus className="h-3.5 w-3.5" /> 新增绑定
              </Button>
            )}
          </div>

          {/* 加载状态 */}
          {loading && (
            <div className="flex items-center justify-center py-12">
              <div className="text-center">
                <div className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-slate-200 border-t-slate-600"></div>
                <p className="mt-3 text-sm text-slate-500">加载中...</p>
              </div>
            </div>
          )}

          {!loading && (
            <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm">
              <Table>
                <TableHeader>
                  <TableRow className="bg-slate-50/60">
                    <TableHead>渠道类型</TableHead>
                    <TableHead>渠道用户</TableHead>
                    <TableHead>群组信息</TableHead>
                    <TableHead>群角色</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>绑定时间</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {channels.map((ch) => (
                    <TableRow key={ch.id}>
                      <TableCell>
                        <span className="flex items-center gap-2">
                          <span
                            className={"h-2.5 w-2.5 rounded-full " + getChannelTypeColor(ch.channelType)}
                          />
                          {getChannelTypeLabel(ch.channelType)}
                        </span>
                      </TableCell>
                      <TableCell>
                        <p className="text-sm">{ch.channelUsername || "-"}</p>
                        <p className="font-mono text-xs text-muted-foreground">
                          {ch.channelUserId}
                        </p>
                      </TableCell>
                      <TableCell>
                        {ch.channelGroupId ? (
                          <div>
                            <p className="text-sm">{ch.channelGroupName || "-"}</p>
                            <p className="font-mono text-xs text-muted-foreground">
                              {ch.channelGroupId}
                            </p>
                          </div>
                        ) : (
                          <span className="text-muted-foreground">单聊</span>
                        )}
                      </TableCell>
                      <TableCell>
                        {ch.channelGroupId ? (
                          <Badge variant="secondary">
                            {GROUP_ROLE_LABEL[ch.groupRole || "member"] || ch.groupRole}
                          </Badge>
                        ) : (
                          "-"
                        )}
                      </TableCell>
                      <TableCell>
                        {ch.status === 1 ? (
                          <Badge className="bg-emerald-500">启用</Badge>
                        ) : (
                          <Badge variant="secondary">禁用</Badge>
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground text-xs">
                        {ch.createTime || "-"}
                      </TableCell>
                      <TableCell className="text-right">
                        {ch.channelGroupId && canEdit && (
                          <Button
                            variant="ghost"
                            size="icon"
                            title="同步群成员"
                            onClick={() => syncMembers(ch)}
                          >
                            <RefreshCw className="h-4 w-4 text-slate-500" />
                          </Button>
                        )}
                        {canEdit && (
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => setEditChannel({ ...ch })}
                          >
                            <Pencil className="h-4 w-4 text-slate-500" />
                          </Button>
                        )}
                        {canRemove && (
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => deleteChannel(ch)}
                          >
                            <Trash2 className="h-4 w-4 text-rose-500" />
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                  {channels.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7} className="py-6 text-center text-muted-foreground">
                        暂无渠道绑定记录
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </section>
          )}
        </div>
      </div>

      {/* 编辑抽屉 */}
      <Sheet open={!!editChannel} onOpenChange={(open) => !open && setEditChannel(null)}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>{editChannel?.id ? "编辑渠道绑定" : "新增渠道绑定"}</SheetTitle>
            <SheetDescription>
              绑定用户与外部渠道，支持单聊和群聊两种模式
            </SheetDescription>
          </SheetHeader>
          {editChannel && (
            <div className="flex-1 space-y-5 overflow-y-auto py-2">
              {/* 渠道类型 */}
              <div className="space-y-1.5">
                <Label>
                  渠道类型 <span className="text-rose-500">*</span>
                </Label>
                <select
                  className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                  value={editChannel.channelType}
                  onChange={(e) =>
                    setEditChannel({ ...editChannel, channelType: e.target.value })
                  }
                >
                  <option value="wechat">微信</option>
                  <option value="slack">Slack</option>
                  <option value="telegram">Telegram</option>
                  <option value="web">Web</option>
                </select>
              </div>

              {/* 渠道用户 ID */}
              <div className="space-y-1.5">
                <Label>
                  渠道用户 ID <span className="text-rose-500">*</span>
                </Label>
                <Input
                  placeholder="如微信 openid、Slack user_id"
                  className="font-mono"
                  value={editChannel.channelUserId}
                  onChange={(e) =>
                    setEditChannel({ ...editChannel, channelUserId: e.target.value })
                  }
                />
              </div>

              {/* 渠道用户显示名 */}
              <div className="space-y-1.5">
                <Label>渠道用户显示名</Label>
                <Input
                  placeholder="如微信昵称"
                  value={editChannel.channelUsername || ""}
                  onChange={(e) =>
                    setEditChannel({ ...editChannel, channelUsername: e.target.value })
                  }
                />
              </div>

              {/* 群组 ID（群聊场景） */}
              <div className="space-y-1.5">
                <Label>群组 ID</Label>
                <Input
                  placeholder="留空表示单聊，填写表示群聊"
                  className="font-mono"
                  value={editChannel.channelGroupId || ""}
                  onChange={(e) =>
                    setEditChannel({ ...editChannel, channelGroupId: e.target.value || null })
                  }
                />
                <p className="text-xs text-muted-foreground">
                  单聊留空；群聊填写群组 ID，群内成员将共享对话上下文
                </p>
              </div>

              {/* 群组名称 */}
              <div className="space-y-1.5">
                <Label>群组名称</Label>
                <Input
                  placeholder="如微信群名"
                  value={editChannel.channelGroupName || ""}
                  onChange={(e) =>
                    setEditChannel({ ...editChannel, channelGroupName: e.target.value })
                  }
                />
              </div>

              {/* 群角色 */}
              {editChannel.channelGroupId && (
                <div className="space-y-1.5">
                  <Label>群内角色</Label>
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                    value={editChannel.groupRole || "member"}
                    onChange={(e) =>
                      setEditChannel({ ...editChannel, groupRole: e.target.value })
                    }
                  >
                    <option value="owner">群主</option>
                    <option value="admin">管理员</option>
                    <option value="member">成员</option>
                  </select>
                </div>
              )}

              {/* 状态 */}
              <div className="space-y-1.5">
                <Label>状态</Label>
                <select
                  className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                  value={editChannel.status ?? 1}
                  onChange={(e) =>
                    setEditChannel({ ...editChannel, status: Number(e.target.value) })
                  }
                >
                  <option value={1}>启用</option>
                  <option value={0}>禁用</option>
                </select>
              </div>
            </div>
          )}
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setEditChannel(null)}>
              取消
            </Button>
            <Button onClick={saveChannel} disabled={saving} className="min-w-24">
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
  return <ChannelPage />;
}
