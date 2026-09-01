"use client";

/**
 * 客户端路由守卫：无 token 跳登录页；可选角色守卫（如管理页仅租户管理员及以上）。
 * 本项目为无状态 JWT，token 存 localStorage，故在客户端组件挂载时校验；
 * 受保护页面统一在顶层调用本 Hook。前端守卫仅为体验优化，后端接口仍有独立鉴权。
 */
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/auth";

export function useAuthGuard(options?: { requireTenantAdmin?: boolean }): { ready: boolean } {
  const router = useRouter();
  const hydrated = useAuthStore((s) => s.hydrated);
  const hydrate = useAuthStore((s) => s.hydrate);
  const token = useAuthStore((s) => s.token);
  const isTenantAdmin = useAuthStore((s) => s.isTenantAdmin)();

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (!hydrated) return;
    if (!token) {
      router.replace("/login");
    } else if (options?.requireTenantAdmin && !isTenantAdmin) {
      // 普通用户直输管理页地址时拦回首页（接口层另有 403 兜底）
      router.replace("/");
    }
  }, [hydrated, token, isTenantAdmin, options?.requireTenantAdmin, router]);

  const roleOk = !options?.requireTenantAdmin || isTenantAdmin;
  return { ready: hydrated && !!token && roleOk };
}
