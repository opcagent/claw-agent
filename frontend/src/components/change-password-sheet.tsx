"use client";

/**
 * 修改密码抽屉：本人修改登录密码（核验原密码）。
 * 修改成功后强制重新登录（旧 token 的密码语义已过期）。
 */
import { useState } from "react";
import { toast } from "sonner";
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
import { useAuthStore } from "@/store/auth";

export default function ChangePasswordSheet({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const logout = useAuthStore((s) => s.logout);
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [saving, setSaving] = useState(false);

  function reset() {
    setOldPassword("");
    setNewPassword("");
    setConfirm("");
  }

  async function save() {
    if (!oldPassword || !newPassword) {
      toast.error("请填写原密码与新密码");
      return;
    }
    if (newPassword.length < 6) {
      toast.error("新密码至少 6 位");
      return;
    }
    if (newPassword !== confirm) {
      toast.error("两次输入的新密码不一致");
      return;
    }
    setSaving(true);
    try {
      await api.put("/api/auth/password", { oldPassword, newPassword });
      toast.success("密码已修改，请使用新密码重新登录");
      onOpenChange(false);
      reset();
      // 强制重新登录：避免旧 token 继续使用造成密码认知不一致
      setTimeout(() => logout(), 800);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "修改失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Sheet
      open={open}
      onOpenChange={(o) => {
        onOpenChange(o);
        if (!o) reset();
      }}
    >
      <SheetContent className="sm:max-w-lg">
        <SheetHeader className="border-b pb-4">
          <SheetTitle>修改密码</SheetTitle>
          <SheetDescription>
            修改成功后将退出登录，请使用新密码重新登录
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-5 overflow-y-auto py-2">
          <div className="space-y-1.5">
            <Label>
              原密码 <span className="text-rose-500">*</span>
            </Label>
            <Input
              type="password"
              autoComplete="current-password"
              value={oldPassword}
              onChange={(e) => setOldPassword(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label>
              新密码 <span className="text-rose-500">*</span>
            </Label>
            <Input
              type="password"
              autoComplete="new-password"
              placeholder="至少 6 位"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label>
              确认新密码 <span className="text-rose-500">*</span>
            </Label>
            <Input
              type="password"
              autoComplete="new-password"
              value={confirm}
              onKeyDown={(e) => e.key === "Enter" && save()}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </div>
        </div>
        <SheetFooter className="border-t pt-4">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={save} disabled={saving} className="min-w-24">
            {saving ? "保存中..." : "确认修改"}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
