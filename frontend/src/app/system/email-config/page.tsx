"use client";

/**
 * 邮箱配置管理：用户 SMTP 邮箱配置的增删改查与测试。
 * 所有登录用户均可管理自己的邮箱配置。
 */
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Mail, Plus, Trash2, Edit, CheckCircle, Send } from "lucide-react";
import AppShell from "@/components/app-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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

interface EmailConfig {
  id: number;
  userId: string;
  tenantId: number;
  smtpHost: string;
  smtpPort: number;
  smtpUsername: string;
  smtpPassword: string;
  smtpUseSsl: boolean;
  smtpUseTls: boolean;
  fromName: string;
  fromEmail: string;
  enabled: boolean;
  defaultFlag: boolean;
  remark?: string;
  createTime: string;
  updateTime: string;
}

interface EmailConfigForm {
  id?: number;
  smtpHost: string;
  smtpPort: number;
  smtpUsername: string;
  smtpPassword: string;
  smtpUseSsl: boolean;
  smtpUseTls: boolean;
  fromName: string;
  fromEmail: string;
  enabled: boolean;
  defaultFlag: boolean;
  remark?: string;
}

const DEFAULT_FORM: EmailConfigForm = {
  smtpHost: "",
  smtpPort: 587,
  smtpUsername: "",
  smtpPassword: "",
  smtpUseSsl: false,
  smtpUseTls: true,
  fromName: "",
  fromEmail: "",
  enabled: true,
  defaultFlag: false,
  remark: "",
};

function EmailConfigPage() {
  useAuthGuard();
  
  const [configs, setConfigs] = useState<EmailConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editForm, setEditForm] = useState<EmailConfigForm>({ ...DEFAULT_FORM });
  const [testOpen, setTestOpen] = useState(false);
  const [testConfig, setTestConfig] = useState<EmailConfig | null>(null);
  const [testTo, setTestTo] = useState("");
  const [saving, setSaving] = useState(false);

  const loadConfigs = useCallback(async () => {
    try {
      setLoading(true);
      const res = await api.get<EmailConfig[]>("/api/emailConfig/list");
      setConfigs(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载配置失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadConfigs();
  }, [loadConfigs]);

  async function handleSave() {
    if (!editForm.smtpHost || !editForm.smtpUsername || !editForm.fromEmail) {
      toast.error("SMTP 服务器、账号和发件人邮箱为必填项");
      return;
    }

    if (!editForm.smtpPassword && !editForm.id) {
      toast.error("密码为必填项");
      return;
    }

    setSaving(true);
    try {
      await api.post("/api/emailConfig/save", editForm);
      toast.success(editForm.id ? "配置已更新" : "配置已添加");
      setEditOpen(false);
      setEditForm({ ...DEFAULT_FORM });
      loadConfigs();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: number) {
    if (!confirm("确定要删除此邮箱配置吗?")) return;

    try {
      await api.del(`/api/emailConfig/delete/${id}`);
      toast.success("配置已删除");
      loadConfigs();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  async function handleSetDefault(config: EmailConfig) {
    try {
      // 先将所有配置设为非默认
      for (const c of configs) {
        if (c.defaultFlag && c.id !== config.id) {
          await api.post("/api/emailConfig/save", {
            ...c,
            defaultFlag: false,
            smtpPassword: "***",
          });
        }
      }

      // 将当前配置设为默认
      await api.post("/api/emailConfig/save", {
        ...config,
        defaultFlag: true,
        smtpPassword: "***",
      });

      toast.success("已设为默认配置");
      loadConfigs();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "设置失败");
    }
  }

  function openEdit(config?: EmailConfig) {
    if (config) {
      setEditForm({
        id: config.id,
        smtpHost: config.smtpHost,
        smtpPort: config.smtpPort,
        smtpUsername: config.smtpUsername,
        smtpPassword: "***",
        smtpUseSsl: config.smtpUseSsl,
        smtpUseTls: config.smtpUseTls,
        fromName: config.fromName,
        fromEmail: config.fromEmail,
        enabled: config.enabled,
        defaultFlag: config.defaultFlag,
        remark: config.remark,
      });
    } else {
      setEditForm({ ...DEFAULT_FORM });
    }
    setEditOpen(true);
  }

  async function handleTest() {
    if (!testTo) {
      toast.error("请输入收件人邮箱");
      return;
    }

    if (!testConfig) return;

    try {
      // 使用 test API,传入配置和收件人
      await api.post(
        `/api/emailConfig/test?to=${encodeURIComponent(testTo)}`,
        testConfig
      );
      toast.success(`测试邮件已发送至: ${testTo}`);
      setTestOpen(false);
      setTestTo("");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "发送失败");
    }
  }

  return (
    <AppShell>
      <div className="space-y-4">
        {/* 顶部操作栏 */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Mail className="h-5 w-5 text-indigo-600" />
            <h1 className="text-xl font-semibold text-slate-800">SMTP 邮箱配置</h1>
            <Badge variant="secondary" className="ml-2">
              {configs.length} 个配置
            </Badge>
          </div>
          <Button onClick={() => openEdit()} className="gap-2">
            <Plus className="h-4 w-4" />
            新增配置
          </Button>
        </div>

        {/* 提示卡片 */}
        <div className="rounded-lg border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800">
          <p className="font-medium mb-2">💡 使用提示:</p>
          <ul className="list-disc list-inside space-y-1 text-xs">
            <li>大多数邮箱服务商要求使用<strong>授权码</strong>而非登录密码</li>
            <li>Gmail: 需开启两步验证,生成应用专用密码</li>
            <li>QQ/163 邮箱: 在设置中开启 SMTP 并获取授权码</li>
            <li>推荐使用端口 <strong>587 (TLS)</strong>,更安全</li>
          </ul>
        </div>

        {/* 配置列表表格 */}
        <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50/60">
                <TableHead>发件人</TableHead>
                <TableHead>SMTP 服务器</TableHead>
                <TableHead>端口</TableHead>
                <TableHead>加密方式</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>默认</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {configs.map((config) => (
                <TableRow key={config.id}>
                  <TableCell>
                    <div>
                      <p className="font-medium">{config.fromName || config.fromEmail}</p>
                      <p className="text-xs text-muted-foreground">{config.fromEmail}</p>
                    </div>
                  </TableCell>
                  <TableCell className="font-mono text-sm">
                    {config.smtpHost}
                  </TableCell>
                  <TableCell>{config.smtpPort}</TableCell>
                  <TableCell>
                    {config.smtpUseSsl ? (
                      <Badge variant="outline" className="bg-green-50 text-green-700">
                        SSL
                      </Badge>
                    ) : config.smtpUseTls ? (
                      <Badge variant="outline" className="bg-blue-50 text-blue-700">
                        TLS
                      </Badge>
                    ) : (
                      <Badge variant="outline" className="bg-slate-50 text-slate-700">
                        无
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    {config.enabled ? (
                      <Badge className="bg-emerald-500">启用</Badge>
                    ) : (
                      <Badge variant="secondary">禁用</Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    {config.defaultFlag ? (
                      <Badge className="bg-amber-500">默认</Badge>
                    ) : (
                      <span className="text-muted-foreground">-</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      {!config.defaultFlag && (
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleSetDefault(config)}
                          title="设为默认"
                        >
                          <CheckCircle className="h-4 w-4 text-amber-600" />
                        </Button>
                      )}
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => {
                          setTestConfig(config);
                          setTestOpen(true);
                        }}
                        title="测试配置"
                      >
                        <Send className="h-4 w-4 text-blue-600" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => openEdit(config)}
                        title="编辑"
                      >
                        <Edit className="h-4 w-4 text-slate-600" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleDelete(config.id)}
                        title="删除"
                      >
                        <Trash2 className="h-4 w-4 text-rose-600" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
              {configs.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    暂无邮箱配置,请点击"新增配置"添加
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      {/* 新增/编辑对话框 */}
      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editForm.id ? "编辑邮箱配置" : "新增邮箱配置"}</DialogTitle>
            <DialogDescription>
              配置 SMTP 服务器信息,用于 Agent 发送邮件通知
            </DialogDescription>
          </DialogHeader>

          <div className="grid grid-cols-2 gap-4 py-4">
            <div className="space-y-2">
              <Label>SMTP 服务器 *</Label>
              <Input
                placeholder="如 smtp.gmail.com"
                value={editForm.smtpHost}
                onChange={(e) => setEditForm({ ...editForm, smtpHost: e.target.value })}
              />
            </div>

            <div className="space-y-2">
              <Label>端口 *</Label>
              <Input
                type="number"
                placeholder="587"
                value={editForm.smtpPort}
                onChange={(e) => setEditForm({ ...editForm, smtpPort: parseInt(e.target.value) || 587 })}
              />
            </div>

            <div className="space-y-2">
              <Label>邮箱账号 *</Label>
              <Input
                placeholder="user@example.com"
                value={editForm.smtpUsername}
                onChange={(e) => setEditForm({ ...editForm, smtpUsername: e.target.value })}
              />
            </div>

            <div className="space-y-2">
              <Label>授权码/密码 {editForm.id && "(留空不修改)"}</Label>
              <Input
                type="password"
                placeholder={editForm.id ? "留空不修改" : "输入授权码"}
                value={editForm.smtpPassword === "***" ? "" : editForm.smtpPassword}
                onChange={(e) => setEditForm({ ...editForm, smtpPassword: e.target.value })}
              />
            </div>

            <div className="space-y-2">
              <Label>发件人显示名称</Label>
              <Input
                placeholder="如: 我的邮箱"
                value={editForm.fromName}
                onChange={(e) => setEditForm({ ...editForm, fromName: e.target.value })}
              />
            </div>

            <div className="space-y-2">
              <Label>发件人邮箱 *</Label>
              <Input
                placeholder="user@example.com"
                value={editForm.fromEmail}
                onChange={(e) => setEditForm({ ...editForm, fromEmail: e.target.value })}
              />
            </div>

            <div className="col-span-2 flex items-center gap-6">
              <label className="flex items-center gap-2">
                <Switch
                  checked={editForm.smtpUseSsl}
                  onCheckedChange={(checked) => setEditForm({ ...editForm, smtpUseSsl: checked })}
                />
                <span className="text-sm">使用 SSL</span>
              </label>

              <label className="flex items-center gap-2">
                <Switch
                  checked={editForm.smtpUseTls}
                  onCheckedChange={(checked) => setEditForm({ ...editForm, smtpUseTls: checked })}
                />
                <span className="text-sm">使用 TLS (推荐)</span>
              </label>

              <label className="flex items-center gap-2">
                <Switch
                  checked={editForm.enabled}
                  onCheckedChange={(checked) => setEditForm({ ...editForm, enabled: checked })}
                />
                <span className="text-sm">启用</span>
              </label>

              <label className="flex items-center gap-2">
                <Switch
                  checked={editForm.defaultFlag}
                  onCheckedChange={(checked) => setEditForm({ ...editForm, defaultFlag: checked })}
                />
                <span className="text-sm">设为默认</span>
              </label>
            </div>

            <div className="col-span-2 space-y-2">
              <Label>备注</Label>
              <Input
                placeholder="如: Gmail 工作邮箱"
                value={editForm.remark || ""}
                onChange={(e) => setEditForm({ ...editForm, remark: e.target.value })}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSave} disabled={saving} className="min-w-24">
              {saving ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 测试对话框 */}
      <Dialog open={testOpen} onOpenChange={setTestOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>测试邮箱配置</DialogTitle>
            <DialogDescription>
              发送测试邮件验证 SMTP 配置是否正确
            </DialogDescription>
          </DialogHeader>

          <div className="py-4 space-y-4">
            <div className="space-y-2">
              <Label>收件人邮箱</Label>
              <Input
                type="email"
                placeholder="test@example.com"
                value={testTo}
                onChange={(e) => setTestTo(e.target.value)}
              />
            </div>

            {testConfig && (
              <div className="rounded-lg bg-slate-50 p-3 text-sm">
                <p className="font-medium mb-1">使用配置:</p>
                <p className="text-muted-foreground">
                  {testConfig.fromName || testConfig.fromEmail} ({testConfig.fromEmail})
                </p>
                <p className="text-muted-foreground text-xs mt-1">
                  SMTP: {testConfig.smtpHost}:{testConfig.smtpPort}
                </p>
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setTestOpen(false)}>
              取消
            </Button>
            <Button onClick={handleTest} className="gap-2">
              <Send className="h-4 w-4" />
              发送测试邮件
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AppShell>
  );
}

export default EmailConfigPage;
