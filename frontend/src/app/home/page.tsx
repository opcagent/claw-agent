"use client";

/**
 * 首页：平台帮助中心。
 * 结构：欢迎横幅（平台名 + 版本 + 快捷入口）→ 快速上手四步 → 平台能力功能卡 → 技能使用 → MCP 扩展 → 使用小贴士。
 * 目的：新用户登录后先看到「怎么用、有什么」，再进入具体功能模块。
 */
import { useEffect, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  Bell,
  BookOpen,
  Bot,
  FileText,
  KeyRound,
  Lightbulb,
  MessageSquare,
  PlugZap,
  ScrollText,
  Settings,
  ShieldCheck,
  Sparkles,
  Workflow,
} from "lucide-react";
import AppShell from "@/components/app-shell";
import { api } from "@/lib/api";
import { useAuthGuard } from "@/lib/use-auth-guard";
import { useAuthStore } from "@/store/auth";
import type { VersionInfo } from "@/lib/types";

/** 快速上手步骤（序号由数组顺序决定，文案与实际操作路径保持一致） */
const QUICK_STEPS = [
  {
    icon: KeyRound,
    title: "配置模型",
    desc: "管理员进入「智能体引擎 → 模型与能力」，添加模型提供商并填写 API Key，模型目录按厂商预置可直接选择。",
    accent: "from-amber-400 to-orange-500",
  },
  {
    icon: Sparkles,
    title: "挑选人格",
    desc: "在「人格预设」浏览平台提供的助手人格（通用助手、写作、编码等），查看系统提示词，选择贴合场景的一个。",
    accent: "from-violet-400 to-indigo-500",
  },
  {
    icon: MessageSquare,
    title: "开始对话",
    desc: "进入「AI 工作台 → 智能对话」，选择人格与会话后直接提问，支持文字、图片与文件多模态输入。",
    accent: "from-sky-400 to-blue-500",
  },
  {
    icon: Workflow,
    title: "自动编排",
    desc: "进阶用户可在「自动化流水线」把多步任务编排成剧本，一次触发按步骤执行，异常策略可自定义。",
    accent: "from-emerald-400 to-teal-500",
  },
];

/** 平台能力功能卡：图标 + 名称 + 说明 + 直达入口 */
const FEATURES = [
  {
    icon: MessageSquare,
    name: "智能对话",
    desc: "流式对话主界面，多会话管理、多模态消息、敏感操作人工确认（HITL）。",
    href: "/",
  },
  {
    icon: Sparkles,
    name: "人格预设",
    desc: "助手人格模板库，平台 / 租户 / 个人三级作用域，支持复用与定制。",
    href: "/presets",
  },
  {
    icon: Workflow,
    name: "自动化流水线",
    desc: "多步任务编排剧本，Markdown 编写步骤与异常策略，按作用域共享。",
    href: "/pipelines",
  },
  {
    icon: Settings,
    name: "模型与能力",
    desc: "模型提供商、运行参数、技能与工具的三级作用域配置，保存即热生效。",
    href: "/system/config",
  },
  {
    icon: ShieldCheck,
    name: "平台治理",
    desc: "成员与账户、角色与权限、菜单权限、组织架构、租户空间与数据字典。",
    href: "/system/user",
  },
  {
    icon: ScrollText,
    name: "审计日志",
    desc: "业务操作日志与登录登出日志，按时间倒序分页查阅，便于追溯。",
    href: "/system/log",
  },
];

/** 技能添加方式：两条路径卡片（自学习 / 手动放文件），无需改代码 */
const SKILL_WAYS = [
  {
    icon: Lightbulb,
    title: "方式一：Agent 自学习",
    desc: "对话中直接说「把刚才的做法沉淀成一个技能」，Agent 会自动起草并走审核流程写入你的技能库；后台每周自动整理，长期不用的技能会被标旧归档。",
    tag: "推荐",
    accent: "from-violet-400 to-indigo-500",
  },
  {
    icon: FileText,
    title: "方式二：手动放置文件",
    desc: "技能就是工作区里的一个 Markdown 文件：在用户工作区 skills/<技能名>/SKILL.md 写入 frontmatter（name / description）与操作指引，保存后刷新技能列表即可见。",
    tag: "进阶",
    accent: "from-sky-400 to-blue-500",
  },
];

/** MCP 扩展：注册与挂载两张卡（在管理页登记，Agent 自动挂载调用） */
const MCP_WAYS = [
  {
    icon: PlugZap,
    title: "第一步：登记 MCP 服务器",
    desc: "管理员进入「智能体引擎 → 模型与能力 → MCP 服务器」，点击新增：填写名称与传输方式——stdio（本地命令 + 参数）、SSE 或 HTTP（服务端点）；可选配请求头 / 环境变量（加密存储），并按全局 / 租户 / 用户三级作用域生效。",
    tag: "管理员",
    accent: "from-emerald-400 to-teal-500",
  },
  {
    icon: Bot,
    title: "第二步：对话中自然调用",
    desc: "启用后保存，受影响用户的 Agent 会自动热重建并挂载该服务器暴露的工具，无需任何代码——直接用自然语言提需求即可，如「搜一下这个文件夹里的内容」，Agent 会自行选用合适的 MCP 工具。",
    tag: "自动生效",
    accent: "from-violet-400 to-indigo-500",
  },
];

/** 使用小贴士：易踩坑点与平台约定 */
const TIPS = [
  "配置采用三级作用域（平台 → 租户 → 个人），同键按下级优先就近覆盖。",
  "模型 API Key 未配置时对话会提示错误，请联系管理员在「模型与能力」中配置。",
  "Agent 执行危险操作（如写入敏感路径）时会暂停并等待人工确认，请放心放行。",
  "右上角铃铛是平台公告入口，版本更新说明会在此触达；侧栏底部展示当前版本号。",
];

function HomeCenterPage() {
  const user = useAuthStore((s) => s.user);
  const [versionInfo, setVersionInfo] = useState<VersionInfo | null>(null);

  useEffect(() => {
    api
      .get<VersionInfo>("/api/config/versionInfo")
      .then((res) => setVersionInfo(res.data || null))
      .catch(() => {});
  }, []);

  return (
    <AppShell>
      <div className="h-full overflow-auto">
        <div className="mx-auto max-w-5xl space-y-8 p-6">
          {/* 欢迎横幅：渐变底 + 平台名/版本 + 快捷入口 */}
          <section className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-indigo-500 via-violet-500 to-purple-500 p-8 text-white shadow-lg shadow-indigo-200/60">
            <div className="relative z-10">
              <div className="mb-2 flex items-center gap-2 text-xs">
                <span className="rounded-full bg-white/20 px-2.5 py-1 font-medium backdrop-blur">
                  {versionInfo ? `${versionInfo.name} · v${versionInfo.version}` : "Claw Agent"}
                </span>
                <span className="rounded-full bg-white/20 px-2.5 py-1 backdrop-blur">帮助中心</span>
              </div>
              <h1 className="text-2xl font-semibold">
                {user?.nickname || user?.username}，欢迎回来
              </h1>
              <p className="mt-2 max-w-2xl text-sm leading-relaxed text-indigo-100">
                这里是你的个人智能体平台：选择一个贴合场景的人格，用对话完成工作；
                把重复任务交给流水线，模型与能力按作用域灵活配置。下面的指引可以帮你快速上手。
              </p>
              <div className="mt-5 flex flex-wrap gap-3">
                <Link
                  href="/"
                  className="flex items-center gap-1.5 rounded-xl bg-white px-4 py-2 text-sm font-medium text-indigo-600 shadow-sm transition-colors hover:bg-indigo-50"
                >
                  开始对话 <ArrowRight className="h-4 w-4" />
                </Link>
                <Link
                  href="/presets"
                  className="flex items-center gap-1.5 rounded-xl border border-white/40 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-white/10"
                >
                  浏览人格预设
                </Link>
              </div>
            </div>
            {/* 装饰性光斑 */}
            <div className="absolute -right-10 -top-10 h-48 w-48 rounded-full bg-white/10 blur-2xl" />
            <div className="absolute -bottom-16 right-24 h-40 w-40 rounded-full bg-white/10 blur-2xl" />
          </section>

          {/* 快速上手：四步流程卡片 */}
          <section>
            <div className="mb-4 flex items-center gap-2">
              <Bot className="h-5 w-5 text-indigo-500" />
              <h2 className="text-lg font-semibold text-slate-800">快速上手</h2>
              <span className="text-sm text-slate-400">四步开始你的第一次对话</span>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {QUICK_STEPS.map((s, i) => (
                <div
                  key={s.title}
                  className="rounded-2xl border border-slate-200/70 bg-white p-5 shadow-sm transition-shadow hover:shadow-md"
                >
                  <div className="mb-3 flex items-center gap-3">
                    <span
                      className={`flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br ${s.accent} text-white shadow-sm`}
                    >
                      <s.icon className="h-4.5 w-4.5" />
                    </span>
                    <span className="text-xs font-medium text-slate-400">第 {i + 1} 步</span>
                  </div>
                  <h3 className="mb-1.5 text-sm font-semibold text-slate-800">{s.title}</h3>
                  <p className="text-xs leading-relaxed text-slate-500">{s.desc}</p>
                </div>
              ))}
            </div>
          </section>

          {/* 平台能力：功能卡网格（点击直达对应模块） */}
          <section>
            <div className="mb-4 flex items-center gap-2">
              <Settings className="h-5 w-5 text-indigo-500" />
              <h2 className="text-lg font-semibold text-slate-800">平台能力</h2>
              <span className="text-sm text-slate-400">点击卡片直达对应模块</span>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {FEATURES.map((f) => (
                <Link
                  key={f.name}
                  href={f.href}
                  className="group rounded-2xl border border-slate-200/70 bg-white p-5 shadow-sm transition-all hover:-translate-y-0.5 hover:border-indigo-200 hover:shadow-md"
                >
                  <div className="mb-3 flex items-center justify-between">
                    <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-50 text-indigo-500 transition-colors group-hover:bg-indigo-500 group-hover:text-white">
                      <f.icon className="h-4.5 w-4.5" />
                    </span>
                    <ArrowRight className="h-4 w-4 text-slate-300 transition-transform group-hover:translate-x-0.5 group-hover:text-indigo-400" />
                  </div>
                  <h3 className="mb-1 text-sm font-semibold text-slate-800">{f.name}</h3>
                  <p className="text-xs leading-relaxed text-slate-500">{f.desc}</p>
                </Link>
              ))}
            </div>
          </section>

          {/* 技能使用：两条添加路径 + 启停入口（技能是工作区文件，无需改代码） */}
          <section>
            <div className="mb-4 flex items-center gap-2">
              <Lightbulb className="h-5 w-5 text-indigo-500" />
              <h2 className="text-lg font-semibold text-slate-800">技能使用</h2>
              <span className="text-sm text-slate-400">技能是工作区文件，添加无需改代码</span>
            </div>
            <div className="grid gap-4 lg:grid-cols-2">
              {SKILL_WAYS.map((w) => (
                <div
                  key={w.title}
                  className="rounded-2xl border border-slate-200/70 bg-white p-5 shadow-sm transition-shadow hover:shadow-md"
                >
                  <div className="mb-3 flex items-center gap-3">
                    <span
                      className={`flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br ${w.accent} text-white shadow-sm`}
                    >
                      <w.icon className="h-4.5 w-4.5" />
                    </span>
                    <h3 className="text-sm font-semibold text-slate-800">{w.title}</h3>
                    <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] font-medium text-indigo-500">
                      {w.tag}
                    </span>
                  </div>
                  <p className="text-xs leading-relaxed text-slate-500">{w.desc}</p>
                </div>
              ))}
            </div>
            <p className="mt-3 rounded-xl bg-indigo-50/60 px-4 py-3 text-xs leading-relaxed text-slate-600">
              启停管理：进入「智能体引擎 → 模型与能力 → 技能管理」，可查看技能清单并按需开关（按作用域就近生效）；
              新技能文件需等 Agent 下次重建时装入，保存一次 Agent 配置即可触发热重建。
            </p>
          </section>

          {/* MCP 扩展：登记 + 自动挂载（接入外部工具无需写代码） */}
          <section>
            <div className="mb-4 flex items-center gap-2">
              <PlugZap className="h-5 w-5 text-indigo-500" />
              <h2 className="text-lg font-semibold text-slate-800">MCP 扩展</h2>
              <span className="text-sm text-slate-400">接入外部工具服务，无需写代码</span>
            </div>
            <div className="grid gap-4 lg:grid-cols-2">
              {MCP_WAYS.map((w) => (
                <div
                  key={w.title}
                  className="rounded-2xl border border-slate-200/70 bg-white p-5 shadow-sm transition-shadow hover:shadow-md"
                >
                  <div className="mb-3 flex items-center gap-3">
                    <span
                      className={`flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br ${w.accent} text-white shadow-sm`}
                    >
                      <w.icon className="h-4.5 w-4.5" />
                    </span>
                    <h3 className="text-sm font-semibold text-slate-800">{w.title}</h3>
                    <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] font-medium text-indigo-500">
                      {w.tag}
                    </span>
                  </div>
                  <p className="text-xs leading-relaxed text-slate-500">{w.desc}</p>
                </div>
              ))}
            </div>
            <p className="mt-3 rounded-xl bg-indigo-50/60 px-4 py-3 text-xs leading-relaxed text-slate-600">
              适用场景：接入浏览器自动化、文件系统、数据库、内部知识库等任何符合 MCP 协议的服务；
              单个服务器连接失败不会阻断 Agent 启动，其余工具照常可用。
            </p>
          </section>

          {/* 使用小贴士 + 版本亮点：双栏收尾 */}
          <section className="grid gap-4 pb-6 lg:grid-cols-2">
            <div className="rounded-2xl border border-slate-200/70 bg-white p-6 shadow-sm">
              <div className="mb-4 flex items-center gap-2">
                <BookOpen className="h-4.5 w-4.5 text-indigo-500" />
                <h2 className="text-base font-semibold text-slate-800">使用小贴士</h2>
              </div>
              <ul className="space-y-2.5">
                {TIPS.map((t) => (
                  <li key={t} className="flex gap-2 text-xs leading-relaxed text-slate-600">
                    <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-indigo-400" />
                    {t}
                  </li>
                ))}
              </ul>
            </div>
            <div className="rounded-2xl border border-slate-200/70 bg-white p-6 shadow-sm">
              <div className="mb-4 flex items-center gap-2">
                <Bell className="h-4.5 w-4.5 text-indigo-500" />
                <h2 className="text-base font-semibold text-slate-800">本版亮点</h2>
                {versionInfo && (
                  <span className="font-mono text-xs text-slate-400">v{versionInfo.version}</span>
                )}
              </div>
              {versionInfo && versionInfo.highlights.length > 0 ? (
                <ul className="space-y-2.5">
                  {versionInfo.highlights.map((h) => (
                    <li key={h} className="flex gap-2 text-xs leading-relaxed text-slate-600">
                      <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-violet-400" />
                      {h}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-xs text-muted-foreground">暂无版本公告，留意右上角铃铛通知。</p>
              )}
            </div>
          </section>
        </div>
      </div>
    </AppShell>
  );
}

export default function Page() {
  const { ready } = useAuthGuard();
  if (!ready) return null;
  return <HomeCenterPage />;
}
