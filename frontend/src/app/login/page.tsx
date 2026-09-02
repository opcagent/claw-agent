"use client";

/**
 * 登录页：无 token 时的唯一入口。
 * 账号由管理员在「用户管理」中创建，不提供自助注册。
 * 登录成功后写入 Zustand 并跳主页。
 * <p>
 * v3 设计：与主界面（app-shell）视觉统一——浅色柔和渐变、白色卡片、indigo-violet 点缀。
 * 左右分栏布局，左侧品牌展示 + 功能亮点，右侧登录表单。移动端自适应为单列。
 */
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Shield, Zap, Brain, Lock } from "lucide-react";
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

/** 左侧面板的功能亮点条目 */
const FEATURES = [
  { icon: Brain, title: "智能对话", desc: "多模型驱动，上下文记忆，精准理解意图" },
  { icon: Zap, title: "工具调用", desc: "搜索、代码执行、数据分析，一句话搞定" },
  { icon: Shield, title: "安全可控", desc: "HITL 权限管控，敏感操作人工确认" },
  { icon: Lock, title: "企业级", desc: "多租户隔离，RBAC 权限，审计日志全链路" },
];

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
      .get<VersionInfo>("/api/config/versionInfo")
      .then((res) => res.data?.name && setBrandName(res.data.name))
      .catch(() => {});
  }, []);

  // 已登录则直接进首页
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
    <div className="flex min-h-screen bg-gradient-to-br from-slate-50 via-white to-indigo-50/50">
      {/* ===== 左侧品牌面板 ===== */}
      <div className="relative hidden w-1/2 flex-col justify-between overflow-hidden bg-gradient-to-br from-indigo-50 via-white to-violet-50 p-12 lg:flex">
        {/* 装饰：淡色网格 */}
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.03]"
          style={{
            backgroundImage:
              "linear-gradient(#6366f1 1px, transparent 1px), linear-gradient(90deg, #6366f1 1px, transparent 1px)",
            backgroundSize: "48px 48px",
          }}
        />
        {/* 装饰：光晕 */}
        <div className="pointer-events-none absolute -left-20 top-1/4 h-80 w-80 rounded-full bg-indigo-200/40 blur-[100px]" />
        <div className="pointer-events-none absolute -right-20 bottom-1/4 h-80 w-80 rounded-full bg-violet-200/30 blur-[100px]" />

        {/* Logo + 品牌名 */}
        <div className="relative z-10">
          <div className="flex items-center gap-3">
            <LogoMark className="h-10 w-10" />
            <span className="text-xl font-bold text-slate-800">{brandName}</span>
          </div>
        </div>

        {/* 主标题 + 功能列表 */}
        <div className="relative z-10 space-y-10">
          <div>
            <h1 className="text-4xl font-bold leading-tight tracking-tight text-slate-800">
              你的智能 Agent
              <br />
              <span className="bg-gradient-to-r from-indigo-500 to-violet-500 bg-clip-text text-transparent">
                全能工作伙伴
              </span>
            </h1>
            <p className="mt-4 max-w-md text-base leading-relaxed text-slate-500">
              融合多模型对话、工具调用、技能自学习与分层记忆，让 AI 真正理解你的工作方式。
            </p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            {FEATURES.map((f) => (
              <div
                key={f.title}
                className="group rounded-xl border border-slate-200/70 bg-white/70 p-4 shadow-sm backdrop-blur-sm transition-all hover:border-indigo-200 hover:shadow-md hover:shadow-indigo-100/50"
              >
                <f.icon className="mb-3 h-5 w-5 text-indigo-500 transition-colors group-hover:text-indigo-600" />
                <h3 className="text-sm font-semibold text-slate-700">{f.title}</h3>
                <p className="mt-1 text-xs leading-relaxed text-slate-400">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>

        {/* 底部版权 */}
        <div className="relative z-10 text-xs text-slate-400">
          &copy; {new Date().getFullYear()} {brandName}. All rights reserved.
        </div>
      </div>

      {/* ===== 右侧登录表单 ===== */}
      <div className="flex flex-1 items-center justify-center p-6 sm:p-10">
        <Card className="w-full max-w-sm border-slate-200/70 shadow-lg shadow-indigo-100/50">
          <CardHeader className="text-center">
            {/* 移动端 Logo */}
            <div className="mx-auto mb-2 flex items-center justify-center gap-3 lg:hidden">
              <LogoMark className="h-9 w-9" />
              <span className="text-lg font-bold text-slate-800">{brandName}</span>
            </div>
            {/* 桌面端标题 */}
            <div className="mx-auto mb-2 flex h-12 w-12 items-center justify-center lg:flex">
              <LogoMark className="h-12 w-12" />
            </div>
            <CardTitle className="text-slate-800">欢迎回来</CardTitle>
            <CardDescription>登录你的账号，开始高效工作之旅</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="username" className="text-slate-700">用户名</Label>
              <Input
                id="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="请输入用户名"
                autoComplete="username"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password" className="text-slate-700">密码</Label>
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
              className="w-full bg-gradient-to-r from-indigo-500 to-violet-500 text-white shadow-sm hover:from-indigo-600 hover:to-violet-600"
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
    </div>
  );
}
