"use client";

/**
 * 应用外壳：顶部一级菜单（目录）+ 左侧二级菜单（页面）+ 主内容区。
 * 导航由后端菜单授权数据驱动（/api/auth/menus）：角色授权页勾选变化直接影响可见菜单，
 * 平台管理员短路全量（后端保证），避免管理员误取消勾选后丢失入口无法自救。
 * 视觉风格：浅色柔和渐变背景、圆角卡片、紫蓝点缀。
 */
import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Bell,
  BookOpen,
  Building2,
  ChevronLeft,
  ChevronRight,
  CircleUserRound,
  Home,
  KeyRound,
  LayoutGrid,
  LogOut,
  MessageSquare,
  Monitor,
  Network,
  ScrollText,
  Settings,
  ShieldCheck,
  Sparkles,
  Square,
  Users,
  Workflow,
} from "lucide-react";
import { LogoBrand } from "@/components/logo";
import ChangePasswordSheet from "@/components/change-password-sheet";
import ProfileSheet from "@/components/profile-sheet";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { api } from "@/lib/api";
import { useAuthStore } from "@/store/auth";
import type { SysMenu, VersionInfo } from "@/lib/types";

/** 通知已读标记的 localStorage 键（存已读版本号，版本变更即重新亮红点） */
const NOTICE_READ_KEY = "claw_notice_read";

/** 菜单 icon 字段（若依风格字符串）到 lucide 图标的映射；未知值回退方块 */
const ICON_MAP: Record<string, React.ComponentType<{ className?: string }>> = {
  home: Home,
  message: MessageSquare,
  chat: MessageSquare,
  sparkles: Sparkles,
  setting: Settings,
  robot: Settings,
  link: Settings,
  slider: Settings,
  user: Users,
  peoples: ShieldCheck,
  "tree-table": LayoutGrid,
  tree: Network,
  build: Building2,
  log: ScrollText,
  monitor: Monitor,
  book: BookOpen,
  workflow: Workflow,
};

/** 二级菜单纯路径匹配（根路径精确匹配，其余前缀匹配） */
function pathActive(path: string, pathname: string): boolean {
  return path === "/" ? pathname === "/" : pathname.startsWith(path);
}

export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  const [menus, setMenus] = useState<SysMenu[]>([]);
  const [pwdOpen, setPwdOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  // 侧边栏折叠状态
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  // 通知中心：平台版本信息（发布说明随部署配置维护）
  const [versionInfo, setVersionInfo] = useState<VersionInfo | null>(null);
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [readVersion, setReadVersion] = useState<string | null>(null);

  // 拉取当前用户可见菜单（失败静默：内容区仍可用，仅导航为空）
  useEffect(() => {
    api
      .get<SysMenu[]>("/api/auth/menus")
      .then((res) => {
        const menuData = res.data || [];
        setMenus(menuData);
        // 调试日志：检查 Token 统计菜单是否存在
        const tokenMenu = menuData.find(m => m.path === '/token-usage');
        if (tokenMenu) {
          console.log('✅ Token 统计菜单已加载:', tokenMenu);
        } else {
          console.warn('️ 未找到 Token 统计菜单');
        }
      })
      .catch(() => {});
    api
      .get<VersionInfo>("/api/config/version-info")
      .then((res) => setVersionInfo(res.data || null))
      .catch(() => {});
    setReadVersion(window.localStorage.getItem(NOTICE_READ_KEY));
  }, []);

  /** 红点：当前版本未被读过即视为未读通知 */
  const hasUnreadNotice = !!versionInfo && readVersion !== versionInfo.version;

  /** 开合通知面板：打开即标记当前版本已读（红点消失） */
  function toggleNotice(open: boolean) {
    setNoticeOpen(open);
    if (open && versionInfo) {
      window.localStorage.setItem(NOTICE_READ_KEY, versionInfo.version);
      setReadVersion(versionInfo.version);
    }
  }

  /** 一级目录（M）与其下二级菜单（C）分组 */
  const { topMenus, childrenByParent } = useMemo(() => {
    const tops = menus.filter((m) => m.menuType === "M");
    const children = new Map<number, SysMenu[]>();
    for (const m of menus) {
      if (m.menuType !== "C" || m.visible !== 1) continue;
      const list = children.get(m.parentId) ?? [];
      list.push(m);
      children.set(m.parentId, list);
    }
    return { topMenus: tops, childrenByParent: children };
  }, [menus]);

  /** 当前激活的一级目录：先按二级菜单路径命中反查，再按目录自身路径精确命中（直达型目录如预设模板），命中不了则回退第一个 */
  const activeTop = useMemo(() => {
    for (const top of topMenus) {
      const subs = childrenByParent.get(top.id) ?? [];
      if (subs.some((s) => pathActive(s.path || "", pathname))) return top;
      if (top.path && top.path === pathname) return top;
    }
    return topMenus[0] ?? null;
  }, [topMenus, childrenByParent, pathname]);

  const sideMenus = activeTop ? childrenByParent.get(activeTop.id) ?? [] : [];
  // 二级仅一项时无需侧栏（点击一级直达，避免冗余结构）
  // 但 Token 统计页面需要侧边栏展示其他系统管理菜单
  const showSidebar = sideMenus.length >= 1;
  
  // 调试日志：检查侧边栏菜单
  if (pathname.includes('token')) {
    console.log('📍 当前路径:', pathname);
    console.log('📍 激活的一级菜单:', activeTop?.menuName, '(ID:', activeTop?.id, ')');
    console.log('📍 所有顶级菜单:', topMenus.map(m => ({ id: m.id, name: m.menuName, path: m.path })));
    console.log('📍 childrenByParent Map:', Array.from(childrenByParent.entries()).map(([k, v]) => ({ parentId: k, count: v.length, menus: v.map(m => m.menuName) })));
    console.log('📍 侧边栏菜单数量:', sideMenus.length);
    console.log('📍 showSidebar:', showSidebar);
    console.log('📍 侧边栏菜单列表:', sideMenus.map(m => ({ id: m.id, name: m.menuName, path: m.path })));
  }

  /** 一级目录的跳转目标：自身无页面，取第一个二级菜单 */
  function topTarget(top: SysMenu): string {
    const subs = childrenByParent.get(top.id) ?? [];
    return subs[0]?.path || top.path || "/";
  }

  return (
    <div className="flex h-screen flex-col bg-gradient-to-br from-slate-50 via-white to-indigo-50/50">
      {/* 顶部导航栏：品牌 + 一级菜单 + 用户区 */}
      <header className="relative flex h-16 shrink-0 items-center border-b border-slate-200/70 bg-white/85 px-5 backdrop-blur">
        {/* 品牌 Logo：点击回首页（帮助中心门户） */}
        <Link href="/home" className="shrink-0">
          <LogoBrand name={versionInfo?.name} />
        </Link>

        {/* 一级菜单（目录）：图标 + 文字的胶囊标签，激活态渐变底 + 白字 */}
        <nav className="mx-6 flex min-w-0 flex-1 items-center gap-1.5 overflow-x-auto [scrollbar-width:none]">
          {topMenus.map((top) => {
            const active = activeTop?.id === top.id;
            const Icon = ICON_MAP[top.icon || ""] ?? LayoutGrid;
            return (
              <Link
                key={top.id}
                href={topTarget(top)}
                className={
                  "flex shrink-0 items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium whitespace-nowrap transition-all duration-200 " +
                  (active
                    ? "bg-gradient-to-r from-indigo-500 to-violet-500 text-white shadow-md shadow-indigo-200"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900")
                }
              >
                <Icon
                  className={
                    "h-4 w-4 " + (active ? "text-white" : "text-slate-400")
                  }
                />
                {top.menuName}
              </Link>
            );
          })}
        </nav>

        {/* 左侧分隔：细竖线区隔导航与用户区 */}
        <div className="mx-1 h-6 w-px shrink-0 bg-slate-200" />

        {/* 右侧：通知 + 用户 */}
        <div className="flex shrink-0 items-center gap-2">
          {/* 通知中心：平台版本公告（内容来自后端版本信息接口），打开即已读 */}
          <DropdownMenu open={noticeOpen} onOpenChange={toggleNotice}>
            <DropdownMenuTrigger className="relative flex h-9 w-9 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700">
              <Bell className="h-4 w-4" />
              {hasUnreadNotice && (
                <span className="absolute right-2 top-2 h-1.5 w-1.5 rounded-full bg-rose-500" />
              )}
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-80">
              <div className="px-4 py-3">
                <div className="mb-1 flex items-baseline justify-between">
                  <span className="text-sm font-medium text-slate-800">平台公告</span>
                  {versionInfo && (
                    <span className="font-mono text-xs text-slate-400">
                      v{versionInfo.version}
                      {versionInfo.releaseDate ? ` · ${versionInfo.releaseDate}` : ""}
                    </span>
                  )}
                </div>
                {versionInfo ? (
                  <>
                    <p className="mb-2 text-xs text-slate-500">
                      {versionInfo.name} 版本更新说明
                    </p>
                    <ul className="space-y-1.5">
                      {versionInfo.highlights.map((h) => (
                        <li key={h} className="flex gap-2 text-xs leading-relaxed text-slate-600">
                          <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-indigo-400" />
                          {h}
                        </li>
                      ))}
                      {versionInfo.highlights.length === 0 && (
                        <li className="text-xs text-muted-foreground">暂无公告</li>
                      )}
                    </ul>
                  </>
                ) : (
                  <p className="text-xs text-muted-foreground">暂无公告</p>
                )}
              </div>
            </DropdownMenuContent>
          </DropdownMenu>
          <DropdownMenu>
            <DropdownMenuTrigger className="flex items-center gap-2 rounded-full border border-slate-200 bg-white py-1 pl-1 pr-3 shadow-sm transition-colors hover:bg-slate-50">
              <Avatar className="h-7 w-7">
                <AvatarFallback className="bg-gradient-to-br from-indigo-500 to-violet-500 text-xs text-white">
                  {(user?.nickname || user?.username || "?").slice(0, 1).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <span className="text-sm text-slate-700">
                {user?.nickname || user?.username}
              </span>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem disabled className="text-xs text-muted-foreground">
                {/* 展示租户名称而非 ID，旧缓存无名称时降级显示 ID */}
                租户：{user?.tenantName || `#${user?.tenantId}`} ｜ 角色：{user?.roles?.join(", ")}
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => setProfileOpen(true)}>
                <CircleUserRound className="mr-2 h-4 w-4" /> 个人信息
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => setPwdOpen(true)}>
                <KeyRound className="mr-2 h-4 w-4" /> 修改密码
              </DropdownMenuItem>
              <DropdownMenuItem onClick={logout}>
                <LogOut className="mr-2 h-4 w-4" /> 退出登录
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </header>

      {/* 主体：左侧二级菜单 + 内容区 */}
      <div className="flex min-h-0 flex-1">
        {showSidebar && (
          <aside 
            className={`relative flex shrink-0 flex-col gap-1 overflow-y-auto border-r border-slate-200/70 bg-white/60 p-3 transition-all duration-300 ${
              sidebarCollapsed ? "w-14" : "w-48"
            }`}
          >
            {/* 折叠按钮 */}
            <button
              onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
              className="absolute -right-3 top-4 z-10 flex h-6 w-6 items-center justify-center rounded-full border border-slate-200 bg-white shadow-sm hover:bg-slate-50 hover:border-indigo-300 transition-colors"
              title={sidebarCollapsed ? "展开侧边栏" : "收起侧边栏"}
            >
              {sidebarCollapsed ? (
                <ChevronRight className="h-3.5 w-3.5 text-slate-500" />
              ) : (
                <ChevronLeft className="h-3.5 w-3.5 text-slate-500" />
              )}
            </button>

            {/* 菜单列表 */}
            {sideMenus.map((m) => {
              const Icon = ICON_MAP[m.icon || ""] ?? Square;
              const active = pathActive(m.path || "", pathname);
              return (
                <Link
                  key={m.id}
                  href={m.path || "/"}
                  className={
                    `flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors ${
                      sidebarCollapsed ? "justify-center" : ""
                    } ` +
                    (active
                      ? "bg-indigo-50 font-medium text-indigo-600"
                      : "text-slate-600 hover:bg-slate-100 hover:text-slate-900")
                  }
                  title={sidebarCollapsed ? m.menuName : undefined}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  {!sidebarCollapsed && <span className="truncate">{m.menuName}</span>}
                </Link>
              );
            })}
            {/* 侧栏底部：平台版本号（来自版本信息接口） */}
            {!sidebarCollapsed && versionInfo && (
              <div className="mt-auto px-3 pb-1 pt-3 text-[11px] text-slate-400">
                {versionInfo.name} · v{versionInfo.version}
              </div>
            )}
          </aside>
        )}
        <main className="min-w-0 flex-1 overflow-hidden">{children}</main>
      </div>

      {/* 修改密码抽屉 */}
      <ChangePasswordSheet open={pwdOpen} onOpenChange={setPwdOpen} />

      {/* 个人信息详情抽屉 */}
      <ProfileSheet open={profileOpen} onOpenChange={setProfileOpen} />
    </div>
  );
}
