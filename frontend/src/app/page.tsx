"use client";

/**
 * 首页：智能对话（聊天主界面）。
 * 受保护页面：未登录自动跳登录页。
 */
import AppShell from "@/components/app-shell";
import ChatView from "@/components/chat-view";
import { useAuthGuard } from "@/lib/use-auth-guard";

export default function HomePage() {
  const { ready } = useAuthGuard();
  if (!ready) return null;

  return (
    <AppShell>
      <ChatView />
    </AppShell>
  );
}
