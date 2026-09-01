"use client";

/**
 * 登录页：无 token 时的唯一入口。
 * 账号由管理员在「用户管理」中创建，不提供自助注册。
 * 登录成功后写入 Zustand 并跳主页。
 */
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { LogoMark } from "@/components/logo";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { api } from "@/lib/api";
import { useAuthStore } from "@/store/auth";
import type { LoginResponse, VersionInfo } from "@/lib/types";

export default function LoginPage() {
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);
  const hydrate = useAuthStore((s) => s.hydrate);
  const token = useAuthStore((s) => s.token);

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [brandName, setBrandName] = useState("Opc Agent");

  // 拉取平台品牌名（来自 yml claw.version.name）
  useEffect(() => {
    api
      .get<VersionInfo>("/api/config/version-info")
      .then((res) => res.data?.name && setBrandName(res.data.name))
      .catch(() => {});
  }, []);

  // 已登录则直接进首页（帮助中心门户）
  useEffect(() => {
    hydrate();
  }, [hydrate]);
  useEffect(() => {
    if (token) router.replace("/home");
  }, [token, router]);

  async function doLogin() {
    if (!username.trim() || !password) {
      toast.error("请输入用户名和密码");
      return;
    }
    setLoading(true);
    try {
      const res = await api.post<LoginResponse>(
        "/api/auth/login",
        { username: username.trim(), password },
        false
      );
      setAuth(res.data);
      toast.success("登录成功");
      router.replace("/home");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "登录失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-violet-100/60 p-4">
      <Card className="w-full max-w-sm border-slate-200/70 shadow-lg shadow-indigo-100/50">
        <CardHeader className="text-center">
          <div className="mx-auto mb-2 flex h-12 w-12 items-center justify-center">
            <LogoMark className="h-12 w-12" />
          </div>
          <CardTitle>{brandName}</CardTitle>
          <CardDescription>个人 Agent 平台 · 欢迎回来，开始高效工作之旅</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="username">用户名</Label>
            <Input
              id="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="请输入用户名"
              autoComplete="username"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">密码</Label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && doLogin()}
              placeholder="请输入密码"
              autoComplete="current-password"
            />
          </div>
          <Button
            className="w-full bg-gradient-to-r from-indigo-500 to-violet-500 shadow-sm hover:from-indigo-600 hover:to-violet-600"
            onClick={doLogin}
            disabled={loading}
          >
            {loading && <Loader2 className="animate-spin" />} 登录
          </Button>
          <p className="text-center text-xs text-muted-foreground">
            账号由管理员统一创建分配，如需开通请联系管理员
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
