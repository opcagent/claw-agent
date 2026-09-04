"use client";

/**
 * 模型与能力：模型提供商与运行参数（三级作用域）。
 * 作用域可见性按角色：GLOBAL 仅平台管理员、TENANT 租户管理员及以上、USER 所有人。
 * 保存后后端发布配置变更事件，受影响用户的 Agent 自动热重建。
 */
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Pencil, Plus, Trash2, ChevronDown, ChevronRight, Wrench, Search, Database, Code, Bot, Settings, Package } from "lucide-react";
import { cn } from "@/lib/utils";
import AppShell from "@/components/app-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { api, modelApi, ModelCard } from "@/lib/api";
import { useAuthGuard } from "@/lib/use-auth-guard";
import { useAuthStore } from "@/store/auth";
import { TOOL_CATEGORY_LABEL } from "@/lib/types";
import { ModelSelector } from "@/components/model-selector";

import type {
  AgentConfigItem,
  DictData,
  McpServer,
  ModelProviderConfig,
  ParamKeyInfo,
  SkillInfo,
  SysUser,
  SystemProps,
  ToolConfig,
  ToolKeyInfo,
  ToolSetWithDetails,
} from "@/lib/types";

type Scope = "USER" | "TENANT" | "PLATFORM";

/** 提供商兑底默认值：字典未就绪时的回退；baseUrl 等字典不承载的字段由此回填 */
const PROVIDER_DEFAULTS: Record<string, { displayName: string; modelName: string; baseUrl?: string }> = {
  dashscope: { displayName: "阿里云通义千问", modelName: "qwen-plus" },
  openai: { displayName: "OpenAI 及兼容协议", modelName: "gpt-4.1-mini", baseUrl: "https://api.openai.com/v1" },
  deepseek: { displayName: "DeepSeek", modelName: "deepseek-chat", baseUrl: "https://api.deepseek.com" },
  ollama: { displayName: "本地 Ollama", modelName: "qwen2.5:7b", baseUrl: "http://localhost:11434" },
  anthropic: { displayName: "Anthropic Claude", modelName: "claude-sonnet-4-20250514", baseUrl: "https://api.anthropic.com" },
  gemini: { displayName: "Google Gemini", modelName: "gemini-2.5-pro", baseUrl: "https://generativelanguage.googleapis.com" },
  volcengine: { displayName: "火山方舟（豆包）", modelName: "doubao-seed-2-1-pro-260628", baseUrl: "https://ark.cn-beijing.volces.com/api/v3" },
};

/** 模型目录字典 dict_value 格式：厂商@模型名（按首个 @ 分隔，模型名自身可含冒号如 qwen2.5:7b） */
function parseProviderModel(dictValue: string): { provider: string; model: string } {
  const i = dictValue.indexOf("@");
  return i < 0
    ? { provider: "", model: dictValue }
    : { provider: dictValue.slice(0, i), model: dictValue.slice(i + 1) };
}

/** 作用域键 → 中文标签 */
const SCOPE_LABEL: Record<Scope, string> = {
  USER: "用户级",
  TENANT: "租户级",
  PLATFORM: "平台",
};

/** MCP 传输方式选项（与 AgentScope McpServerRegistrar 支持的取值一致） */
const MCP_TRANSPORTS = [
  { value: "stdio", label: "stdio（本地命令）" },
  { value: "sse", label: "SSE（Server-Sent Events）" },
  { value: "http", label: "HTTP（Streamable HTTP 简写）" },
  { value: "streamable-http", label: "Streamable HTTP" },
];

/** 配置页左侧菜单项：perm 对应 sys_menu F 类型权限标识，通过角色授权页面灵活配置 */
const CONFIG_MENU: { key: string; label: string; icon: typeof Bot; comment: string; perm: string }[] = [
  { key: "model", label: "模型提供商", icon: Bot, comment: "模型提供商卡片", perm: "agent:model:view" },
  { key: "params", label: "运行参数", icon: Settings, comment: "运行参数卡片", perm: "agent:param:view" },
  { key: "tools", label: "工具集管理", icon: Wrench, comment: "工具集管理卡片", perm: "agent:tool:view" },
  { key: "search", label: "搜索引擎", icon: Search, comment: "搜索引擎 API Key 配置卡片", perm: "agent:search:view" },
  { key: "mcp", label: "MCP 服务器", icon: Database, comment: "MCP 服务器", perm: "agent:mcp:view" },
  { key: "skills", label: "技能管理", icon: Code, comment: "技能管理卡片", perm: "agent:skill:view" },
  { key: "system", label: "平台配置", icon: Package, comment: "平台系统配置", perm: "agent:system:view" },
];

function ConfigPage() {
  const isAdmin = useAuthStore((s) => s.isAdmin)();
  const isTenantAdmin = useAuthStore((s) => s.isTenantAdmin)();
  const myUsername = useAuthStore((s) => s.user)?.username ?? "";

  // 根据角色确定作用域：admin→PLATFORM / tenant_admin→TENANT / common→USER
  const scope: Scope = isAdmin ? "PLATFORM" : isTenantAdmin ? "TENANT" : "USER";
  const [providers, setProviders] = useState<ModelProviderConfig[]>([]);
  const [params, setParams] = useState<AgentConfigItem[]>([]);
  const [paramKeys, setParamKeys] = useState<ParamKeyInfo[]>([]);
  const [providerModels, setProviderModels] = useState<DictData[]>([]);
  /** 提供商下拉选项（字典 agent_model_provider，失败时回退 PROVIDER_DEFAULTS） */
  const [providerOptions, setProviderOptions] = useState<{ value: string; label: string }[]>([]);
  const [systemProps, setSystemProps] = useState<SystemProps | null>(null);
  const [editProvider, setEditProvider] = useState<ModelProviderConfig | null>(null);
  const [editParam, setEditParam] = useState<AgentConfigItem | null>(null);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(false);

  // 能力配置：工具开关 / MCP 服务器（随作用域）与技能管理（随目标用户）
  const [toolKeys, setToolKeys] = useState<ToolKeyInfo[]>([]);
  const [toolConfigs, setToolConfigs] = useState<ToolConfig[]>([]);
  const [toolSets, setToolSets] = useState<ToolSetWithDetails[]>([]);
  const [expandedToolSets, setExpandedToolSets] = useState<Set<string>>(new Set());
  const [mcps, setMcps] = useState<McpServer[]>([]);
  const [editMcp, setEditMcp] = useState<McpServer | null>(null);
  const [skillUsers, setSkillUsers] = useState<SysUser[]>([]);
  const [skillUser, setSkillUser] = useState<string>("");
  const [skills, setSkills] = useState<SkillInfo[]>([]);

  // 搜索引擎 API Key 配置
  const [searchConfigs, setSearchConfigs] = useState<AgentConfigItem[]>([]);
  const [editSearchKey, setEditSearchKey] = useState<string>("");
  const [editSearchValue, setEditSearchValue] = useState<string>("");
  const [savingSearch, setSavingSearch] = useState(false);

  // 按权限标识过滤侧边栏：权限来自 sys_role_menu 授权，管理员可在「菜单权限」页面灵活配置
  const hasPerm = useAuthStore((s) => s.hasPerm);
  const visibleMenu = CONFIG_MENU.filter((item) => hasPerm(item.perm));

  // 左侧菜单导航：hash 同步，URL 与菜单名一致
  const [activeTab, setActiveTab] = useState(visibleMenu[0]?.key ?? "model");
  useEffect(() => {
    const hash = window.location.hash.replace("#", "");
    if (hash && visibleMenu.some((m) => m.key === hash)) setActiveTab(hash);
    const onHash = () => {
      const h = window.location.hash.replace("#", "");
      if (h && visibleMenu.some((m) => m.key === h)) setActiveTab(h);
    };
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, [visibleMenu]);
  const switchTab = (key: string) => {
    setActiveTab(key);
    window.location.hash = key;
  };
  const activeMenu = visibleMenu.find((m) => m.key === activeTab) ?? visibleMenu[0];

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [p, c, t, m, ts, sc] = await Promise.all([
        api.get<ModelProviderConfig[]>(`/api/config/providers?scope=${scope}`),
        api.get<AgentConfigItem[]>(`/api/config/params?scope=${scope}`),
        api.get<ToolConfig[]>(`/api/capability/toolConfigs?scope=${scope}`),
        api.get<McpServer[]>(`/api/capability/mcp?scope=${scope}`),
        api.get<ToolSetWithDetails[]>("/api/tools/details"),  // ← 工具集是全局的,不传 scope
        api.get<AgentConfigItem[]>(`/api/config/searchConfigs?scope=${scope}`),
      ]);
      setProviders(p.data || []);
      setParams(c.data || []);
      setToolConfigs(t.data || []);
      setMcps(m.data || []);
      setToolSets(ts.data || []);
      setSearchConfigs(sc.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }, [scope]);

  useEffect(() => {
    // 切换作用域时先清空数据,避免显示旧数据
    setProviders([]);
    setParams([]);
    setToolConfigs([]);
    setMcps([]);
    setToolSets([]);      // ← 清空工具集
    setSearchConfigs([]); // ← 清空搜索引擎
    setSkills([]);        // ← 清空技能
    load();
  }, [load]);

  // 参数目录、模型目录字典与系统配置不随作用域变化，只拉一次；失败静默（非关键路径）
  useEffect(() => {
    api
      .get<ParamKeyInfo[]>("/api/config/paramKeys")
      .then((res) => setParamKeys(res.data || []))
      .catch(() => {});
    api
      .get<SystemProps>("/api/config/systemProps")
      .then((res) => setSystemProps(res.data || null))
      .catch(() => {});
    api
      .get<DictData[]>("/api/dict/data/agent_provider_models")
      .then((res) => setProviderModels(res.data || []))
      .catch(() => {});
    // 提供商下拉：字典 agent_model_provider（label=中文名, value=厂商键）
    api
      .get<DictData[]>("/api/dict/data/agent_model_provider")
      .then((res) => {
        const list = (res.data || []).map((d) => ({ value: d.dictValue, label: d.dictLabel }));
        if (list.length > 0) setProviderOptions(list);
      })
      .catch(() => {});
    api
      .get<ToolKeyInfo[]>("/api/capability/toolKeys")
      .then((res) => setToolKeys(res.data || []))
      .catch(() => {});
  }, []);  // ← 只在组件挂载时加载一次,不随 scope 变化

  // 技能管理：管理员可切换目标用户（限本租户），普通用户只看自己；
  // 用户列表仅管理员拉取，失败静默
  useEffect(() => {
    if (!(isAdmin || isTenantAdmin)) return;
    api
      .get<SysUser[]>("/api/adminUser/list")
      .then((res) => setSkillUsers(res.data || []))
      .catch(() => {});
  }, [isAdmin, isTenantAdmin]);

  const loadSkills = useCallback(async () => {
    try {
      // 技能按用户隔离,不传 scope;但需要重新加载以反映当前用户
      const query = skillUser ? `?username=${encodeURIComponent(skillUser)}` : "";
      const res = await api.get<SkillInfo[]>(`/api/capability/skills${query}`);
      setSkills(res.data || []);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载技能失败");
    }
  }, [skillUser]);  // ← 移除 scope 依赖

  useEffect(() => {
    loadSkills();
  }, [loadSkills]);

  /** 当前编辑的参数对应的目录项（控制值输入框形态：枚举下拉 / 自由文本） */
  const editingKeyInfo = editParam
    ? paramKeys.find((k) => k.key === editParam.configKey) ?? null
    : null;

  /** 某厂商的候选模型（字典维护，按显示顺序） */
  function modelsOf(provider: string): DictData[] {
    return providerModels.filter(
      (d) => parseProviderModel(d.dictValue).provider === provider
    );
  }

  /** 厂商默认模型：字典 is_default 优先，其次首项；字典未就绪时回退前端兑底值 */
  function defaultModelOf(provider: string): string {
    const list = modelsOf(provider);
    const picked = list.find((d) => d.defaultFlag === 1) ?? list[0];
    if (picked) return parseProviderModel(picked.dictValue).model;
    return PROVIDER_DEFAULTS[provider]?.modelName ?? "";
  }

  async function saveProvider() {
    if (!editProvider) return;
    setSaving(true);
    try {
      await api.post("/api/config/providers", { ...editProvider, scope });
      toast.success("已保存，相关用户的 Agent 将热重建");
      setEditProvider(null);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function saveParam() {
    if (!editParam) return;
    setSaving(true);
    try {
      await api.post("/api/config/params", {
        scope,
        configKey: editParam.configKey,
        configValue: editParam.configValue,
        remark: editParam.remark,
      });
      toast.success("已保存");
      setEditParam(null);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  // ---------------- 搜索引擎 API Key 配置 ----------------

  /** 搜索引擎配置键 → 显示名/占位符/是否密码框 */
  const SEARCH_ENGINE_FIELDS: { key: string; label: string; placeholder: string; isPassword: boolean }[] = [
    { key: "search.tavily.api_key", label: "Tavily API Key", placeholder: "https://app.tavily.com 注册获取（免费 1000 次/月）", isPassword: true },
    { key: "search.brave.api_key", label: "Brave API Key", placeholder: "https://brave.com/search/api 注册获取（~1000 次/月免费）", isPassword: true },
    { key: "search.bing.api_key", label: "Bing API Key", placeholder: "Azure 门户创建 Bing Search v7 资源获取", isPassword: true },
    { key: "search.searxng.base_url", label: "SearXNG 实例地址", placeholder: "http://localhost:8888 或公共实例地址", isPassword: false },
  ];

  /** 获取某搜索配置项的当前值（**** 表示已配置但未回显明文） */
  function searchConfigValue(configKey: string): string {
    return searchConfigs.find((c) => c.configKey === configKey)?.configValue ?? "";
  }

  /** 判断某搜索配置项是否已配置（**** 也算已配置） */
  function searchConfigured(configKey: string): boolean {
    const v = searchConfigValue(configKey);
    return v !== "";
  }

  async function saveSearchConfig(configKey: string) {
    const value = editSearchValue.trim();
    // 空值或脱敏值跳过，保留原密钥
    if (!value || value === "****") {
      setEditSearchKey("");
      setEditSearchValue("");
      return;
    }
    setSavingSearch(true);
    try {
      await api.post("/api/config/searchConfigs", { scope, configKey, configValue: value });
      toast.success("已保存");
      setEditSearchKey("");
      setEditSearchValue("");
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSavingSearch(false);
    }
  }

  // ---------------- 工具开关 ----------------

  /** 某工具在当前作用域下的开关状态（未登记视为默认启用） */
  function toolEnabled(key: string): boolean {
    const row = toolConfigs.find((c) => c.toolKey === key);
    return row ? row.enabled === 1 : true;
  }

  async function toggleTool(key: string, enabled: boolean) {
    try {
      await api.post("/api/capability/toolConfig", { scope, toolKey: key, enabled });
      toast.success("已保存，相关用户的 Agent 将热重建");
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    }
  }

  // ---------------- 工具集管理（新 API） ----------------

  /** 切换工具集的启用/禁用状态 */
  async function toggleToolSet(code: string, enabled: boolean) {
    try {
      const endpoint = enabled ? `/api/tools/${code}/enable` : `/api/tools/${code}/disable`;
      await api.post(endpoint);
      toast.success(`工具集已${enabled ? "启用" : "禁用"}，相关用户的 Agent 将热重建`);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "操作失败");
    }
  }

  /** 切换工具集卡片的展开/折叠 */
  function toggleToolSetExpand(code: string) {
    setExpandedToolSets((prev) => {
      const next = new Set(prev);
      if (next.has(code)) {
        next.delete(code);
      } else {
        next.add(code);
      }
      return next;
    });
  }

  /** 按分类分组工具集 */
  function groupToolSetsByCategory() {
    const groups: Record<string, ToolSetWithDetails[]> = {};
    for (const ts of toolSets) {
      const cat = ts.category || "utility";
      if (!groups[cat]) groups[cat] = [];
      groups[cat].push(ts);
    }
    return groups;
  }

  /** 分类对应的图标组件 */
  function CategoryIcon({ category }: { category: string }) {
    const iconProps = { className: "h-4 w-4" };
    switch (category) {
      case "search": return <Search {...iconProps} />;
      case "data": return <Database {...iconProps} />;
      case "code": return <Code {...iconProps} />;
      case "ai": return <Bot {...iconProps} />;
      case "system": return <Settings {...iconProps} />;
      default: return <Wrench {...iconProps} />;
    }
  }

  // ---------------- MCP 服务器 ----------------

  /** 传输方式英文值 → 中文展示名 */
  function transportLabel(value: string): string {
    return MCP_TRANSPORTS.find((t) => t.value === value)?.label ?? value;
  }

  /** 某 MCP 服务器连接目标的简要展示（stdio 显示命令，其余显示端点） */
  function mcpTarget(m: McpServer): string {
    return m.transport === "stdio" ? m.command || "-" : m.url || "-";
  }

  async function saveMcp() {
    if (!editMcp) return;
    setSaving(true);
    try {
      await api.post("/api/capability/mcp", { ...editMcp, scope });
      toast.success("已保存，相关用户的 Agent 将热重建");
      setEditMcp(null);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function deleteMcp(m: McpServer) {
    if (!m.id) return;
    if (!window.confirm(`删除 MCP 服务器「${m.name}」？`)) return;
    try {
      await api.del(`/api/capability/mcp/${m.id}?scope=${scope}`);
      toast.success("已删除");
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    }
  }

  // ---------------- 技能管理 ----------------

  async function toggleSkill(name: string, enabled: boolean) {
    try {
      await api.post("/api/capability/skillToggle", {
        username: skillUser || undefined,
        skillName: name,
        enabled,
      });
      toast.success("已保存，该用户的 Agent 将热重建");
      loadSkills();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    }
  }

  return (
    <AppShell>
      <div className="flex h-full overflow-hidden">
        {/* 左侧菜单 */}
        <div className="w-48 shrink-0 border-r border-slate-200/70 bg-slate-50/50 p-3">
          <nav className="space-y-0.5">
            {visibleMenu.map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.key;
              return (
                <button
                  key={item.key}
                  onClick={() => switchTab(item.key)}
                  className={cn(
                    "flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-sm transition-colors",
                    isActive
                      ? "bg-white font-medium text-slate-800 shadow-sm"
                      : "text-slate-500 hover:bg-white/60 hover:text-slate-700"
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </button>
              );
            })}
          </nav>
        </div>

        {/* 右侧内容 */}
        <div className="flex-1 overflow-auto p-6">
          <div className="space-y-4">
            {/* 页头（平台管理固定平台级别，无作用域切换） */}
            <div className="flex items-center justify-between">
              <div>
                <h1 className="text-xl font-semibold text-slate-800">{activeMenu.label}</h1>
                <p className="text-sm text-slate-500">
                  {activeTab === "system"
                    ? "系统级配置，全局生效（需重启服务）"
                    : activeTab === "tools" || activeTab === "skills"
                    ? "全局/用户级数据，不随作用域变化"
                    : scope === "PLATFORM"
                    ? "平台级别配置，全局生效"
                    : scope === "TENANT"
                    ? "租户级别配置，本租户生效"
                    : "用户级别配置，仅本人生效"}
                </p>
              </div>
            </div>
          {loading && (
            <div className="flex items-center justify-center py-12">
              <div className="text-center">
                <div className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-slate-200 border-t-slate-600"></div>
                <p className="mt-3 text-sm text-slate-500">加载中...</p>
              </div>
            </div>
          )}

          {!loading && activeTab === "model" && (
          /* 模型提供商卡片 */
          <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-medium text-slate-700">模型提供商</h2>
              <Button
                size="sm"
                variant="outline"
                className="gap-1"
                onClick={() =>
                  setEditProvider({
                    scope,
                    provider: "dashscope",
                    displayName: PROVIDER_DEFAULTS.dashscope.displayName,
                    enabled: 1,
                    isCurrent: 0,
                    modelName: defaultModelOf("dashscope"),
                  })
                }
              >
                <Plus className="h-3.5 w-3.5" /> 新增
              </Button>
            </div>
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/60">
                  <TableHead>提供商</TableHead>
                  <TableHead>名称</TableHead>
                  <TableHead>模型</TableHead>
                  <TableHead>Base URL</TableHead>
                  <TableHead>生效</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {providers.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell>{p.provider}</TableCell>
                    <TableCell>{p.displayName}</TableCell>
                    <TableCell className="font-mono text-xs">{p.modelName}</TableCell>
                    <TableCell className="max-w-40 truncate text-xs text-muted-foreground">
                      {p.baseUrl || "（默认）"}
                    </TableCell>
                    <TableCell>
                      {p.isCurrent === 1 ? (
                        <Badge className="bg-indigo-500">当前</Badge>
                      ) : p.enabled === 0 ? (
                        <Badge variant="secondary">已禁用</Badge>
                      ) : (
                        <Badge variant="outline">备用</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setEditProvider({ ...p, apiKey: "" })}
                      >
                        <Pencil className="h-4 w-4 text-slate-500" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {providers.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} className="py-6 text-center text-muted-foreground">
                      该作用域暂无配置（将继承上级作用域）
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </section>
          )}

          {!loading && activeTab === "params" && (
          /* 运行参数卡片 */
          <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-medium text-slate-700">运行参数</h2>
              <Button
                size="sm"
                variant="outline"
                className="gap-1"
                onClick={() => setEditParam({ scope, configKey: "", configValue: "" })}
              >
                <Plus className="h-3.5 w-3.5" /> 新增
              </Button>
            </div>
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/60">
                  <TableHead>配置键</TableHead>
                  <TableHead>值</TableHead>
                  <TableHead>说明</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {params.map((c) => (
                  <TableRow key={c.configKey}>
                    <TableCell className="font-mono text-xs">{c.configKey}</TableCell>
                    <TableCell className="font-mono text-xs">{c.configValue}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{c.remark || "-"}</TableCell>
                    <TableCell className="text-right">
                      <Button variant="ghost" size="icon" onClick={() => setEditParam({ ...c })}>
                        <Pencil className="h-4 w-4 text-slate-500" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {params.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="py-6 text-center text-muted-foreground">
                      该作用域暂无配置（将继承上级作用域）
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </section>
          )}

          {!loading && activeTab === "tools" && (
          <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <h2 className="font-medium text-slate-700">工具集管理</h2>
                <Badge variant="secondary" className="text-xs">
                  {toolSets.length} 个工具集
                </Badge>
              </div>
              <span className="text-xs text-muted-foreground">
                按分类分组 · 点击展开查看具体工具方法
              </span>
            </div>

            {toolSets.length === 0 ? (
              <div className="py-8 text-center text-muted-foreground">
                <Package className="mx-auto mb-2 h-8 w-8 text-slate-300" />
                <p>工具集加载中…</p>
              </div>
            ) : (
              <div className="space-y-3">
                {Object.entries(groupToolSetsByCategory()).map(([category, sets]) => (
                  <div key={category} className="rounded-lg border border-slate-100 bg-slate-50/30 p-3">
                    {/* 分类标题 */}
                    <div className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-600">
                      <CategoryIcon category={category} />
                      <span>{TOOL_CATEGORY_LABEL[category] || category}</span>
                      <Badge variant="outline" className="text-xs">
                        {sets.length} 个
                      </Badge>
                    </div>

                    {/* 工具集列表 */}
                    <div className="space-y-2">
                      {sets.map((ts) => (
                        <div
                          key={ts.code}
                          className="rounded-lg border border-slate-200/60 bg-white"
                        >
                          {/* 工具集头部：可点击展开 */}
                          <div
                            className="flex cursor-pointer items-center gap-3 p-3 hover:bg-slate-50/50 transition-colors"
                            onClick={() => toggleToolSetExpand(ts.code)}
                          >
                            {/* 展开/折叠图标 */}
                            {expandedToolSets.has(ts.code) ? (
                              <ChevronDown className="h-4 w-4 text-slate-400" />
                            ) : (
                              <ChevronRight className="h-4 w-4 text-slate-400" />
                            )}

                            {/* 工具集信息 */}
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2">
                                <span className="font-medium text-slate-700">{ts.name}</span>
                                <code className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500">
                                  {ts.code}
                                </code>
                                <Badge variant="outline" className="text-xs">
                                  v{ts.version}
                                </Badge>
                                {ts.requiresHITL && (
                                  <Badge className="bg-amber-500 text-xs">需审批</Badge>
                                )}
                              </div>
                              <p className="mt-0.5 truncate text-xs text-muted-foreground">
                                {ts.description || "暂无描述"}
                              </p>
                            </div>

                            {/* 工具数量 + 启用开关 */}
                            <div className="flex items-center gap-3">
                              <span className="text-xs text-muted-foreground">
                                {ts.tools.length} 个工具
                              </span>
                              <input
                                type="checkbox"
                                className="h-4 w-4 accent-indigo-500"
                                checked={ts.enabled}
                                onChange={(e) => {
                                  e.stopPropagation();
                                  toggleToolSet(ts.code, e.target.checked);
                                }}
                                onClick={(e) => e.stopPropagation()}
                              />
                            </div>
                          </div>

                          {/* 展开的工具详情列表 */}
                          {expandedToolSets.has(ts.code) && ts.tools.length > 0 && (
                            <div className="border-t border-slate-100 bg-slate-50/30 p-3">
                              <div className="mb-2 text-xs font-medium text-slate-500">
                                包含的工具方法：
                              </div>
                              <div className="space-y-1.5">
                                {ts.tools.map((tool) => (
                                  <div
                                    key={tool.name}
                                    className="rounded-md border border-slate-200/50 bg-white px-3 py-2"
                                  >
                                    <div className="flex items-start justify-between gap-2">
                                      <div className="flex-1 min-w-0">
                                        <code className="text-xs font-semibold text-indigo-600">
                                          {tool.name}
                                        </code>
                                        <p className="mt-0.5 text-xs text-slate-600">
                                          {tool.description || "暂无描述"}
                                        </p>
                                      </div>
                                      <Badge variant="outline" className="shrink-0 text-xs">
                                        返回: {tool.returnType}
                                      </Badge>
                                    </div>
                                    {tool.parameters.length > 0 && (
                                      <div className="mt-1.5 flex flex-wrap gap-1.5">
                                        {tool.parameters.map((param) => (
                                          <span
                                            key={param.name}
                                            className="inline-flex items-center gap-1 rounded bg-slate-100 px-1.5 py-0.5 text-xs"
                                          >
                                            <code className="text-slate-700">{param.name}</code>
                                            <span className="text-slate-400">:</span>
                                            <span className="text-slate-500">{param.type}</span>
                                            {param.required && (
                                              <span className="text-rose-500">*</span>
                                            )}
                                          </span>
                                        ))}
                                      </div>
                                    )}
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* 展开但无工具 */}
                          {expandedToolSets.has(ts.code) && ts.tools.length === 0 && (
                            <div className="border-t border-slate-100 bg-slate-50/30 p-3 text-center text-xs text-muted-foreground">
                              该工具集暂无具体工具方法
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
          )}

          {!loading && activeTab === "search" && (
          <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <h2 className="font-medium text-slate-700">搜索引擎 API Key</h2>
                <Badge variant="secondary" className="text-xs">
                  {SEARCH_ENGINE_FIELDS.filter((f) => searchConfigured(f.key)).length} / {SEARCH_ENGINE_FIELDS.length} 已配置
                </Badge>
              </div>
              <span className="text-xs text-muted-foreground">
                三级作用域 · 加密存储 · 搜索时自动降级
              </span>
            </div>

            <div className="space-y-3">
              {SEARCH_ENGINE_FIELDS.map((field) => {
                const currentValue = searchConfigValue(field.key);
                const isEditing = editSearchKey === field.key;
                const isConfigured = searchConfigured(field.key);

                return (
                  <div key={field.key} className="rounded-lg border border-slate-100 bg-slate-50/30 p-3">
                    <div className="flex items-center gap-3">
                      {/* 状态指示 */}
                      <div className={`h-2.5 w-2.5 rounded-full ${isConfigured ? "bg-emerald-500" : "bg-slate-300"}`} />

                      {/* 标签 */}
                      <div className="min-w-[140px]">
                        <div className="text-sm font-medium text-slate-700">{field.label}</div>
                        <div className="text-xs text-muted-foreground truncate max-w-[200px]" title={field.placeholder}>
                          {field.placeholder}
                        </div>
                      </div>

                      {/* 值显示/编辑 */}
                      <div className="flex-1">
                        {isEditing ? (
                          <div className="flex gap-2">
                            <Input
                              type={field.isPassword ? "password" : "text"}
                              placeholder={field.placeholder}
                              value={editSearchValue}
                              onChange={(e) => setEditSearchValue(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === "Enter") saveSearchConfig(field.key);
                                if (e.key === "Escape") { setEditSearchKey(""); setEditSearchValue(""); }
                              }}
                              className="h-8 text-sm"
                              autoFocus
                            />
                            <Button
                              size="sm"
                              onClick={() => saveSearchConfig(field.key)}
                              disabled={savingSearch}
                            >
                              保存
                            </Button>
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => { setEditSearchKey(""); setEditSearchValue(""); }}
                            >
                              取消
                            </Button>
                          </div>
                        ) : (
                          <div
                            className="flex cursor-pointer items-center gap-2 text-sm text-slate-600 hover:text-slate-900"
                            onClick={() => {
                              setEditSearchKey(field.key);
                              setEditSearchValue("");
                            }}
                          >
                            {isConfigured ? (
                              <>
                                <span className="font-mono text-xs text-emerald-600">****</span>
                                <span className="text-xs text-muted-foreground">已配置（点击修改）</span>
                              </>
                            ) : (
                              <span className="text-xs text-muted-foreground">未配置（点击设置）</span>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* 降级链说明 */}
            <div className="mt-3 rounded-lg bg-blue-50/50 p-3 text-xs text-blue-700">
              <strong>降级顺序：</strong>Tavily → Brave → Bing → SearXNG → DuckDuckGo
              <br />
              <span className="text-blue-600">
                搜索时按优先级依次尝试，任一引擎成功即返回；未配置的引擎自动跳过；DuckDuckGo 始终兜底。
              </span>
            </div>
          </section>
          )}

          {!loading && activeTab === "mcp" && (
          <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-medium text-slate-700">MCP 服务器</h2>
              <Button
                size="sm"
                variant="outline"
                className="gap-1"
                onClick={() =>
                  setEditMcp({ scope, name: "", transport: "streamable-http", enabled: 1 })
                }
              >
                <Plus className="h-3.5 w-3.5" /> 新增
              </Button>
            </div>
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/60">
                  <TableHead>名称</TableHead>
                  <TableHead>传输方式</TableHead>
                  <TableHead>连接目标</TableHead>
                  <TableHead>生效</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {mcps.map((m) => (
                  <TableRow key={m.id}>
                    <TableCell className="text-sm">{m.name}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{transportLabel(m.transport)}</TableCell>
                    <TableCell className="max-w-48 truncate font-mono text-xs text-muted-foreground" title={mcpTarget(m)}>
                      {mcpTarget(m)}
                    </TableCell>
                    <TableCell>
                      {m.enabled === 1 ? (
                        <Badge className="bg-emerald-500">启用</Badge>
                      ) : (
                        <Badge variant="secondary">已禁用</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button variant="ghost" size="icon" onClick={() => setEditMcp({ ...m, headers: "", env: "" })}>
                        <Pencil className="h-4 w-4 text-slate-500" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => deleteMcp(m)}>
                        <Trash2 className="h-4 w-4 text-rose-500" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {mcps.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="py-6 text-center text-muted-foreground">
                      该作用域暂无 MCP 服务器
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </section>
          )}

          {!loading && activeTab === "skills" && (
          <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-medium text-slate-700">技能管理</h2>
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground">目标用户</span>
                <select
                  className="h-8 rounded-md border bg-background px-2 text-sm"
                  value={skillUser}
                  onChange={(e) => setSkillUser(e.target.value)}
                >
                  <option value="">{myUsername ? `${myUsername}（本人）` : "本人"}</option>
                  {(isAdmin || isTenantAdmin)
                    ? skillUsers
                        .filter((u) => u.username !== myUsername)
                        .map((u) => (
                          <option key={u.id} value={u.username}>
                            {u.nickname ? `${u.nickname}（${u.username}）` : u.username}
                          </option>
                        ))
                    : null}
                </select>
              </div>
            </div>
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/60">
                  <TableHead>技能名</TableHead>
                  <TableHead>描述</TableHead>
                  <TableHead className="text-right">启用</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {skills.map((s) => (
                  <TableRow key={s.name}>
                    <TableCell className="font-mono text-xs">{s.name}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{s.description || "-"}</TableCell>
                    <TableCell className="text-right">
                      <input
                        type="checkbox"
                        className="h-4 w-4 accent-indigo-500"
                        checked={s.enabled}
                        onChange={(e) => toggleSkill(s.name, e.target.checked)}
                      />
                    </TableCell>
                  </TableRow>
                ))}
                {skills.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} className="py-6 text-center text-muted-foreground">
                      该用户工作区暂无技能（Agent 自学习产生的技能会出现在这里）
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </section>
          )}

          {!loading && activeTab === "system" && (
          /* 平台系统配置（只读） */
          systemProps && (
            <section className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="font-medium text-slate-700">平台系统配置（只读）</h2>
                <span className="text-xs text-muted-foreground">
                  来源系统配置，修改后需重启服务生效
                </span>
              </div>
              <dl className="grid grid-cols-1 gap-x-8 gap-y-3 text-sm sm:grid-cols-2">
                <div className="flex justify-between gap-4 border-b border-slate-100 pb-2">
                  <dt className="shrink-0 text-muted-foreground">Agent 名称</dt>
                  <dd className="font-mono text-xs">{systemProps.agentName}</dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-slate-100 pb-2">
                  <dt className="shrink-0 text-muted-foreground">单文件上限</dt>
                  <dd className="font-mono text-xs">{systemProps.uploadMaxSizeMb} MB</dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-slate-100 pb-2">
                  <dt className="shrink-0 text-muted-foreground">token 有效期</dt>
                  <dd className="font-mono text-xs">{systemProps.jwtExpirationHours} 小时</dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-slate-100 pb-2">
                  <dt className="shrink-0 text-muted-foreground">跨域放行源</dt>
                  <dd
                    className="truncate font-mono text-xs"
                    title={systemProps.corsAllowedOrigins.join(", ")}
                  >
                    {systemProps.corsAllowedOrigins.join("，")}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 sm:col-span-2">
                  <dt className="shrink-0 text-muted-foreground">上传扩展名白名单</dt>
                  <dd className="text-right text-xs text-slate-600">
                    {systemProps.uploadAllowedExtensions.join(" / ")}
                  </dd>
                </div>
              </dl>
            </section>
          ))}
          </div>
        </div>
      </div>

      {/* 提供商编辑：右侧抽屉 */}
      <Sheet open={!!editProvider} onOpenChange={(open) => !open && setEditProvider(null)}>
        <SheetContent className="sm:max-w-xl">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>模型提供商配置（{SCOPE_LABEL[scope]}）</SheetTitle>
            <SheetDescription>
              保存后受影响用户的 Agent 自动热重建；API Key 加密存储。
            </SheetDescription>
          </SheetHeader>
          {editProvider && (
            <div className="flex-1 space-y-5 overflow-y-auto py-2">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label>提供商</Label>
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                    value={editProvider.provider}
                    onChange={(e) => {
                      const value = e.target.value;
                      // 新增记录（无 id）切换提供商时回填默认值（模型名取自字典目录），编辑已有记录不覆盖用户配置
                      const defaults = PROVIDER_DEFAULTS[value];
                      if (!editProvider.id && defaults) {
                        setEditProvider({
                          ...editProvider,
                          provider: value,
                          displayName: defaults.displayName,
                          modelName: defaultModelOf(value),
                          baseUrl: defaults.baseUrl || "",
                        });
                      } else {
                        setEditProvider({ ...editProvider, provider: value });
                      }
                    }}
                  >
                    {(providerOptions.length > 0 ? providerOptions : Object.entries(PROVIDER_DEFAULTS).map(([k, v]) => ({ value: k, label: v.displayName }))).map((p) => (
                      <option key={p.value} value={p.value}>
                        {p.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1.5">
                  <Label>展示名称</Label>
                  <Input
                    value={editProvider.displayName}
                    onChange={(e) => setEditProvider({ ...editProvider, displayName: e.target.value })}
                  />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label>模型名</Label>
                {/* 使用动态模型选择器替代静态下拉框 */}
                <ModelSelector 
                  provider={editProvider.provider} 
                  onModelChange={(model: ModelCard) => {
                    setEditProvider({
                      ...editProvider,
                      modelName: model.modelName,
                    });
                  }}
                  defaultValue={editProvider.modelName}
                />
                <p className="text-xs text-muted-foreground">
                  从提供商动态获取模型列表，支持实时查看模型上下文大小
                </p>
              </div>
              <div className="space-y-1.5">
                <Label>Base URL（可选）</Label>
                <Input
                  placeholder="OpenAI 兼容端点；DeepSeek 留空走官方"
                  className="font-mono"
                  value={editProvider.baseUrl || ""}
                  onChange={(e) => setEditProvider({ ...editProvider, baseUrl: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>API Key（留空保留原值，加密存储）</Label>
                <Input
                  type="password"
                  value={editProvider.apiKey || ""}
                  onChange={(e) => setEditProvider({ ...editProvider, apiKey: e.target.value })}
                />
              </div>
              <label className="flex items-center gap-2.5 rounded-lg border bg-slate-50/60 px-3 py-2.5 text-sm">
                <input
                  type="checkbox"
                  className="h-4 w-4 accent-indigo-500"
                  checked={editProvider.isCurrent === 1}
                  onChange={(e) =>
                    setEditProvider({ ...editProvider, isCurrent: e.target.checked ? 1 : 0 })
                  }
                />
                设为该作用域当前生效提供商
              </label>
            </div>
          )}
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setEditProvider(null)}>
              取消
            </Button>
            <Button onClick={saveProvider} disabled={saving} className="min-w-24">
              {saving ? "保存中..." : "保存"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>

      {/* 参数编辑：右侧抽屉 */}
      <Sheet open={!!editParam} onOpenChange={(open) => !open && setEditParam(null)}>
        <SheetContent className="sm:max-w-lg">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>运行参数（{SCOPE_LABEL[scope]}）</SheetTitle>
            <SheetDescription>参数按作用域就近覆盖，同键下级优先</SheetDescription>
          </SheetHeader>
          {editParam && (
            <div className="flex-1 space-y-5 overflow-y-auto py-2">
              <div className="space-y-1.5">
                <Label>配置键</Label>
                {editParam.id ? (
                  <Input className="font-mono" value={editParam.configKey} disabled />
                ) : (
                  // 新增从目录选择：自动回填默认值与说明，避免凭空猜键名；
                  // 目录外的自定义键留「自定义」选项兼容扩展
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                    value={paramKeys.some((k) => k.key === editParam.configKey) ? editParam.configKey : "__custom__"}
                    onChange={(e) => {
                      const key = e.target.value;
                      if (key === "__custom__") {
                        setEditParam({ ...editParam, configKey: "", configValue: "", remark: "" });
                        return;
                      }
                      const info = paramKeys.find((k) => k.key === key);
                      setEditParam({
                        ...editParam,
                        configKey: key,
                        configValue: info?.defaultValue ?? "",
                        remark: info?.description ?? "",
                      });
                    }}
                  >
                    {paramKeys.map((k) => (
                      <option key={k.key} value={k.key}>
                        {k.key}（{(k.description || "").slice(0, 18)}…）
                      </option>
                    ))}
                    <option value="__custom__">自定义键…</option>
                  </select>
                )}
                {!editParam.id && !paramKeys.some((k) => k.key === editParam.configKey) && (
                  <Input
                    placeholder="自定义配置键（小写下划线）"
                    className="mt-2 font-mono"
                    value={editParam.configKey}
                    onChange={(e) => setEditParam({ ...editParam, configKey: e.target.value })}
                  />
                )}
              </div>
              <div className="space-y-1.5">
                <Label>值</Label>
                {editingKeyInfo?.options ? (
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                    value={editParam.configValue}
                    onChange={(e) => setEditParam({ ...editParam, configValue: e.target.value })}
                  >
                    {editingKeyInfo.options.map((o) => (
                      <option key={o} value={o}>
                        {o}
                      </option>
                    ))}
                  </select>
                ) : (
                  <Input
                    value={editParam.configValue}
                    onChange={(e) => setEditParam({ ...editParam, configValue: e.target.value })}
                  />
                )}
                {editingKeyInfo && (
                  <p className="text-xs text-muted-foreground">
                    {editingKeyInfo.description || "-"}（全局默认：{editingKeyInfo.defaultValue}）
                  </p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label>说明</Label>
                <Input
                  value={editParam.remark || ""}
                  onChange={(e) => setEditParam({ ...editParam, remark: e.target.value })}
                />
              </div>
            </div>
          )}
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setEditParam(null)}>
              取消
            </Button>
            <Button onClick={saveParam} disabled={saving} className="min-w-24">
              {saving ? "保存中..." : "保存"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>

      {/* MCP 服务器编辑：右侧抽屉；headers / env 留空保留原密文 */}
      <Sheet open={!!editMcp} onOpenChange={(open) => !open && setEditMcp(null)}>
        <SheetContent className="sm:max-w-xl">
          <SheetHeader className="border-b pb-4">
            <SheetTitle>MCP 服务器（{SCOPE_LABEL[scope]}）</SheetTitle>
            <SheetDescription>
              保存后受影响用户的 Agent 热重建并挂载该服务器的工具；headers / env 加密存储。
            </SheetDescription>
          </SheetHeader>
          {editMcp && (
            <div className="flex-1 space-y-5 overflow-y-auto py-2">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label>名称（唯一）</Label>
                  <Input
                    placeholder="如 amap-maps"
                    className="font-mono"
                    value={editMcp.name}
                    disabled={!!editMcp.id}
                    onChange={(e) => setEditMcp({ ...editMcp, name: e.target.value })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label>传输方式</Label>
                  <select
                    className="h-9 w-full rounded-md border bg-background px-2 text-sm"
                    value={editMcp.transport}
                    onChange={(e) => setEditMcp({ ...editMcp, transport: e.target.value })}
                  >
                    {MCP_TRANSPORTS.map((t) => (
                      <option key={t.value} value={t.value}>
                        {t.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              {editMcp.transport === "stdio" ? (
                <>
                  <div className="space-y-1.5">
                    <Label>启动命令</Label>
                    <Input
                      placeholder="如 npx / uvx / node"
                      className="font-mono"
                      value={editMcp.command || ""}
                      onChange={(e) => setEditMcp({ ...editMcp, command: e.target.value })}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label>启动参数（JSON 数组，可选）</Label>
                    <Input
                      placeholder='如 ["-y", "@amap/amap-maps-mcp-server"]'
                      className="font-mono"
                      value={editMcp.args || ""}
                      onChange={(e) => setEditMcp({ ...editMcp, args: e.target.value })}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label>环境变量（JSON 对象，留空保留原值）</Label>
                    <Input
                      placeholder='如 {"AMAP_MAPS_API_KEY":"..."}'
                      className="font-mono"
                      value={editMcp.env || ""}
                      onChange={(e) => setEditMcp({ ...editMcp, env: e.target.value })}
                    />
                  </div>
                </>
              ) : (
                <>
                  <div className="space-y-1.5">
                    <Label>服务端点</Label>
                    <Input
                      placeholder="如 https://example.com/mcp"
                      className="font-mono"
                      value={editMcp.url || ""}
                      onChange={(e) => setEditMcp({ ...editMcp, url: e.target.value })}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label>请求头（JSON 对象，留空保留原值）</Label>
                    <Input
                      placeholder='如 {"Authorization":"Bearer ..."}'
                      className="font-mono"
                      value={editMcp.headers || ""}
                      onChange={(e) => setEditMcp({ ...editMcp, headers: e.target.value })}
                    />
                  </div>
                </>
              )}
              <div className="space-y-1.5">
                <Label>仅启用的工具名（JSON 数组，留空=全部启用）</Label>
                <Input
                  placeholder='如 ["maps_geo", "maps_search"]'
                  className="font-mono"
                  value={editMcp.enableTools || ""}
                  onChange={(e) => setEditMcp({ ...editMcp, enableTools: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>备注</Label>
                <Input
                  value={editMcp.remark || ""}
                  onChange={(e) => setEditMcp({ ...editMcp, remark: e.target.value })}
                />
              </div>
              <label className="flex items-center gap-2.5 rounded-lg border bg-slate-50/60 px-3 py-2.5 text-sm">
                <input
                  type="checkbox"
                  className="h-4 w-4 accent-indigo-500"
                  checked={editMcp.enabled === 1}
                  onChange={(e) => setEditMcp({ ...editMcp, enabled: e.target.checked ? 1 : 0 })}
                />
                启用该 MCP 服务器
              </label>
            </div>
          )}
          <SheetFooter className="border-t pt-4">
            <Button variant="outline" onClick={() => setEditMcp(null)}>
              取消
            </Button>
            <Button onClick={saveMcp} disabled={saving} className="min-w-24">
              {saving ? "保存中..." : "保存"}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </AppShell>
  );
}

export default function Page() {
  const { ready } = useAuthGuard();
  if (!ready) return null;
  return <ConfigPage />;
}
