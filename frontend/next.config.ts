import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Next.js dev 安全机制默认拦截非白名单 Origin 对 /_next 资源的跨站访问（返回 403），
  // 白名单仅含 localhost 类域名；用 127.0.0.1/局域网 IP 访问需在此登记，否则页面白屏。
  allowedDevOrigins: ["127.0.0.1", "*.local"],
  // 关闭开发调试入口：左下角悬浮的 DevTools 浮标与编译状态指示灯，
  // 避免演示/验收时被误点弹出调试面板遮挡界面（Next.js 15+ 布尔形即同时关闭两者）
  devIndicators: false,
  // 前后端分离：开发期把 /api 同源代理到 Spring Boot（8080），
  // 避免浏览器跨域；生产可直连（后端已配 CORS）或经 Nginx 转发。
  async rewrites() {
    const backend = process.env.BACKEND_URL ?? "http://localhost:8080";
    return [
      {
        source: "/api/:path*",
        destination: `${backend}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
