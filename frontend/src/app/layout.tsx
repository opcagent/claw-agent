import type { Metadata } from "next";
import { Toaster } from "@/components/ui/sonner";
import "./globals.css";

/**
 * 使用系统字体栈，避免启动时访问 Google Fonts（国内网络不通会报警告）。
 * CSS 变量 --font-sans / --font-mono 供 Tailwind / 全局样式引用。
 */
const fontSans = `system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif`;
const fontMono = `ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, "Liberation Mono", monospace`;

export const metadata: Metadata = {
  title: "Opc Agent 平台",
  description: "基于 AgentScope 的个人 Agent 平台（多模型 / RBAC / 用户隔离）",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="zh-CN"
      className="h-full antialiased"
      style={{ "--font-sans": fontSans, "--font-mono": fontMono } as React.CSSProperties}
    >
      <body className="min-h-full flex flex-col">
        {children}
        <Toaster richColors position="top-center" />
      </body>
    </html>
  );
}
