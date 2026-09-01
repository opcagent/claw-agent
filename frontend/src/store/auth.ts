"use client";

/**
 * 认证状态（Zustand）：token 与用户信息持久化到 localStorage，
 * 与后端 JWT 契约对齐（登录响应 LoginResponse）。
 */
import { create } from "zustand";
import type { LoginResponse } from "@/lib/types";
import { TOKEN_KEY } from "@/lib/api";

const USER_KEY = "claw_user";

interface AuthState {
  token: string | null;
  user: LoginResponse | null;
  /** 是否已从 localStorage 恢复（避免 SSR 闪烁） */
  hydrated: boolean;
  /** 从本地存储恢复登录态（页面挂载时调用） */
  hydrate: () => void;
  /** 登录成功后写入 */
  setAuth: (resp: LoginResponse) => void;
  /** 登出：清本地状态并跳登录页 */
  logout: () => void;
  /** 是否平台管理员 */
  isAdmin: () => boolean;
  /** 是否租户管理员及以上 */
  isTenantAdmin: () => boolean;
  /** 是否持有指定按钮权限点（如 system:user:add），通配 *:*:* 视为全持 */
  hasPerm: (perm: string) => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token: null,
  user: null,
  hydrated: false,

  hydrate: () => {
    if (typeof window === "undefined") return;
    const token = localStorage.getItem(TOKEN_KEY);
    let user: LoginResponse | null = null;
    try {
      user = JSON.parse(localStorage.getItem(USER_KEY) || "null");
    } catch {
      user = null;
    }
    set({ token, user, hydrated: true });
  },

  setAuth: (resp) => {
    localStorage.setItem(TOKEN_KEY, resp.token);
    localStorage.setItem(USER_KEY, JSON.stringify(resp));
    set({ token: resp.token, user: resp, hydrated: true });
  },

  logout: () => {
    // 先通知后端记录登出日志（失败不阻断本地登出）
    const token = get().token;
    if (token) {
      // keepalive 保证页面跳转时请求不被中止，登出日志才能可靠落库
      fetch("/api/auth/logout", {
        method: "POST",
        headers: { Authorization: "Bearer " + token },
        keepalive: true,
      }).catch(() => {});
    }
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    set({ token: null, user: null });
    window.location.href = "/login";
  },

  isAdmin: () => get().user?.roles?.includes("admin") ?? false,

  isTenantAdmin: () => {
    const roles = get().user?.roles ?? [];
    return roles.includes("admin") || roles.includes("tenant_admin");
  },

  hasPerm: (perm) => {
    // 权限点来自登录响应（后端按角色授权菜单聚合），仅作展示层控制，后端仍强制鉴权
    const perms = get().user?.permissions ?? [];
    return perms.includes("*:*:*") || perms.includes(perm);
  },
}));
