/**
 * 与后端 DTO 对齐的 TypeScript 类型契约。
 * 后端接口统一返回 Result<T>；SSE 事件为 ChatEvent。
 */

/** 统一响应体（对应 common/Result.java） */
export interface Result<T> {
  code: number;
  message: string;
  data: T;
}

/** 分页视图（对应 dto/PageResult.java） */
export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

/**
 * 业务错误码常量（与后端 ResultCode 枚举一一对应）。
 * 前端按 code 做针对性交互；硬编码数字需与后端同步维护。
 */
export const ERROR_CODES = {
  /** 请求参数错误 */
  PARAM_ERROR: 400,
  /** 未认证 */
  UNAUTHORIZED: 401,
  /** 无权限 */
  FORBIDDEN: 403,
  /** 资源不存在 */
  NOT_FOUND: 404,
  /** 用户名或密码错误 */
  LOGIN_FAILED: 1001,
  /** 账号已被禁用 */
  USER_DISABLED: 1002,
  /** 用户名已存在 */
  USER_EXISTS: 1003,
  /** 文件上传失败 */
  UPLOAD_ERROR: 2001,
  /** 文件大小超限 */
  FILE_TOO_LARGE: 2002,
  /** Agent 调用失败 */
  AGENT_ERROR: 3001,
  /** 流水线不存在 */
  PIPELINE_NOT_FOUND: 4001,
  /** 流水线编码已存在 */
  PIPELINE_CODE_EXISTS: 4002,
  /** 流水线不存在或已禁用 */
  PIPELINE_DISABLED: 4003,
  /** 预设模板不存在 */
  PRESET_NOT_FOUND: 5001,
  /** 预设编码已存在 */
  PRESET_CODE_EXISTS: 5002,
  /** 预设模板不存在或已禁用 */
  PRESET_DISABLED: 5003,
  /** 服务正在更新中（优雅停机） */
  SERVICE_UNAVAILABLE: 503,
  /** 请求过于频繁（限流） */
  RATE_LIMITED: 429,
} as const;

/** 登录响应（对应 dto/LoginResponse.java） */
export interface LoginResponse {
  token: string;
  username: string;
  nickname: string | null;
  tenantId: number;
  tenantName: string | null;
  roles: string[];
  permissions: string[];
}

/** 组织简要信息（myTenants 接口返回，供组织切换使用） */
export interface TenantBrief {
  tenantId: number;
  tenantCode: string;
  tenantName: string;
  roleKeys: string[];
  isDefault: boolean;
}

/** 个人信息详情（对应 dto/ProfileResponse.java） */
export interface ProfileInfo {
  username: string;
  nickname: string | null;
  phone: string | null;
  email: string | null;
  gender: number;
  tenantId: number | null;
  tenantName: string | null;
  roleKeys: string[];
  roleNames: string[];
  createTime: string | null;
  lastLoginTime: string | null;
  lastLoginIp: string | null;
}

/** 在线用户监控条目（对应 dto/OnlineUserVO.java） */
export interface OnlineUser {
  userId: string | null;
  username: string;
  nickname: string | null;
  tenantId: number | null;
  tenantName: string | null;
  lastActiveTime: string;
  lastIp: string | null;
  /** 最近活跃时间在在线阈值内 */
  online: boolean;
}

/** 聊天会话元数据（对应 model/ChatSession.java） */
export interface ChatSession {
  id: number;
  tenantId: number;
  sessionId: string;
  username: string;
  title: string | null;
  /** 会话摘要（后端对话结束后自动生成） */
  summary: string | null;
  /** 是否归档：0 活跃 1 已归档 */
  archived: number;
  createTime: string;
  updateTime: string;
}

/** 聊天消息记录（对应 model/ChatMessage.java，历史会话回看用） */
export interface ChatMessage {
  id: number;
  sessionId: string;
  role: "user" | "assistant";
  content: string | null;
  /** 附件文件名 JSON 数组（仅用户消息，后端序列化为字符串） */
  attachments: string | null;
  /** 1 正常 / 0 失败 */
  status: number;
  createTime: string;
}

/** 待确认工具调用（HITL） */
export interface PendingToolCall {
  toolCallId: string;
  toolName: string;
  toolInput: string | null;
}

/** SSE 聊天事件（对应 dto/ChatEvent.java） */
export interface ChatEvent {
  type: string;
  sessionId?: string;
  replyId?: string;
  delta?: string;
  toolCallId?: string;
  toolName?: string;
  toolInput?: string;
  state?: string;
  subagentId?: string;
  label?: string;
  pendingToolCalls?: PendingToolCall[];
  message?: string;
  /** 流水线当前步骤（progress 事件） */
  progressStep?: number;
  /** 流水线总步数（progress 事件） */
  progressTotal?: number;
  /** 流水线名称（progress 事件） */
  progressLabel?: string;
}

/** 聊天请求（对应 dto/ChatRequest.java） */
export interface ChatRequest {
  sessionId?: string | null;
  content: string;
  presetCode?: string | null;
  pipelineCode?: string | null;
  attachments?: string[] | null;
}

/** HITL 确认请求（对应 dto/ConfirmRequest.java） */
export interface ConfirmRequest {
  sessionId: string;
  approved: boolean;
}

/** 上传响应 */
export interface UploadResponse {
  fileName: string;
  originalName: string;
}

/** 预设 Agent 模板（对应 model/AgentPreset.java） */
export interface AgentPreset {
  id: number;
  scope: "PLATFORM" | "TENANT" | "USER";
  tenantId: number;
  ownerId: string | null;
  agentCode: string;
  agentName: string;
  icon: string | null;
  description: string | null;
  sysPrompt: string;
  orderNum: number;
  enabled: number;
  /** 是否发布到市场：0 否 1 是 */
  published?: number;
  /** 发布名称（市场展示用） */
  publishName?: string | null;
  /** 发布描述 */
  publishDesc?: string | null;
  /** 使用次数 */
  useCount?: number;
  /** 作者名称 */
  authorName?: string | null;
}

/** 编排流水线模板（对应 model/AgentPipeline.java） */
export interface AgentPipeline {
  id?: number;
  scope: "PLATFORM" | "TENANT" | "USER";
  tenantId?: number;
  ownerId?: string | null;
  pipelineCode: string;
  pipelineName: string;
  description?: string | null;
  /** 执行步骤（Markdown：Step N + 动作 + 输出） */
  steps: string;
  /** 异常处理策略（Markdown） */
  exceptionHandling?: string | null;
  orderNum?: number;
  enabled: number;
}

/** 模型提供商配置（对应 model/ModelProviderConfig.java） */
export interface ModelProviderConfig {
  id?: number;
  scope: "PLATFORM" | "TENANT" | "USER";
  tenantId?: number;
  ownerId?: string | null;
  provider: string;
  displayName: string;
  enabled: number;
  isCurrent: number;
  apiKey?: string | null;
  baseUrl?: string | null;
  modelName: string;
  extraConfig?: string | null;
  remark?: string | null;
}

/** Agent 运行参数项（对应 model/AgentConfigItem.java） */
export interface AgentConfigItem {
  id?: number;
  scope: "PLATFORM" | "TENANT" | "USER";
  configKey: string;
  configValue: string;
  remark?: string | null;
}

/** 字典类型（对应 model/DictType.java，管理端维护） */
export interface DictType {
  id?: number;
  tenantId?: number;
  dictName: string;
  dictType: string;
  status: number;
  remark?: string | null;
}

/** 字典数据（对应 model/DictData.java） */
export interface DictData {
  id?: number;
  tenantId?: number;
  dictType: string;
  dictLabel: string;
  dictValue: string;
  dictSort: number;
  cssClass?: string | null;
  defaultFlag: number;
  status: number;
  remark?: string | null;
}

/** 运行参数目录项（对应 dto/ParamKeyInfo.java） */
export interface ParamKeyInfo {
  key: string;
  description: string;
  defaultValue: string;
  options?: string[] | null;
}

/** 平台级系统配置只读视图（对应 ConfigController.SystemProps，已脱敏不含部署路径） */
export interface SystemProps {
  uploadMaxSizeMb: number;
  uploadAllowedExtensions: string[];
  jwtExpirationHours: number;
  corsAllowedOrigins: string[];
  agentName: string;
}

/** 平台版本信息（对应 ConfigController.VersionInfo，通知中心与页脚展示） */
export interface VersionInfo {
  version: string;
  name: string;
  releaseDate: string | null;
  highlights: string[];
}

/** 系统用户（对应 model/User.java，密码不下发） */
export interface SysUser {
  id: string;
  tenantId: number;
  deptId?: number | null;
  username: string;
  nickname: string | null;
  phone?: string | null;
  email?: string | null;
  /** 0 未知 / 1 男 / 2 女 */
  gender?: number;
  status: number;
  remark?: string | null;
  createTime: string;
}

/** 新增用户请求（对应 UserController.UserCreateRequest） */
export interface UserCreateRequest {
  username: string;
  password: string;
  nickname?: string | null;
  phone?: string | null;
  email?: string | null;
  gender?: number;
  deptId?: number | null;
  remark?: string | null;
  /** 职位 */
  position?: string | null;
}

/** 用户-组织关联（对应 model/UserTenant.java） */
export interface UserTenant {
  id?: number;
  userId: string;
  tenantId: number;
  roleId: number;
  deptId?: number | null;
  /** 该组织内的职位 */
  position?: string | null;
  status: number;
  isDefault: number;
  createTime?: string;
}

/** 租户（对应 model/Tenant.java） */
export interface SysTenant {
  id?: number;
  tenantCode: string;
  tenantName: string;
  status: number;
  remark?: string | null;
  createTime?: string;

  // ==================== 新增租户时的初始管理员信息（可选） ====================
  /** 管理员用户名 */
  adminUsername?: string;
  /** 管理员密码 */
  adminPassword?: string;
  /** 管理员昵称 */
  adminNickname?: string;
  /** 管理员手机号 */
  adminPhone?: string;
  /** 管理员邮箱 */
  adminEmail?: string;
  /** 管理员性别：0-未知 / 1-男 / 2-女 */
  adminGender?: number;
}

/** 部门（对应 model/Dept.java，扁平列表前端组树） */
export interface SysDept {
  id?: number;
  tenantId?: number;
  parentId: number;
  ancestors?: string;
  deptName: string;
  orderNum?: number;
  leader?: string | null;
  status: number;
  createTime?: string;
}

/** 角色（对应 model/Role.java） */
export interface SysRole {
  id?: number;
  tenantId?: number;
  roleName: string;
  roleKey: string;
  roleSort?: number;
  dataScope?: number;
  status: number;
  remark?: string | null;
  createTime?: string;
}

/** 菜单/权限点（对应 model/Menu.java） */
export interface SysMenu {
  id: number;
  parentId: number;
  menuName: string;
  menuType: "M" | "C" | "F";
  orderNum: number;
  path?: string | null;
  icon?: string | null;
  perms?: string | null;
  visible: number;
  status: number;
}

/** 业务操作日志（对应 model/OperLog.java） */
export interface OperLog {
  id: number;
  tenantId?: number | null;
  module: string;
  operType: "CREATE" | "UPDATE" | "DELETE" | "GRANT" | "OTHER";
  operDesc: string;
  status: number;
  errorMsg?: string | null;
  operName: string;
  ip?: string | null;
  operTime: string;
}

/** 登录日志（对应 model/LoginLog.java） */
export interface LoginLog {
  id: number;
  username: string;
  tenantId?: number | null;
  eventType: "LOGIN" | "LOGOUT";
  status: number;
  msg?: string | null;
  ip?: string | null;
  loginTime: string;
}

/** MCP 服务器登记（对应 model/McpServer.java，三级作用域） */
export interface McpServer {
  id?: number;
  scope: "PLATFORM" | "TENANT" | "USER";
  tenantId?: number;
  ownerId?: string | null;
  name: string;
  /** stdio / sse / http / streamable-http */
  transport: string;
  command?: string | null;
  /** stdio 启动参数 JSON 数组字符串 */
  args?: string | null;
  url?: string | null;
  /** HTTP 请求头 JSON（列表回显为掩码） */
  headers?: string | null;
  /** stdio 环境变量 JSON（列表回显为掩码） */
  env?: string | null;
  /** 仅启用的工具名 JSON 数组（留空=全部启用） */
  enableTools?: string | null;
  enabled: number;
  remark?: string | null;
}

/** 可开关工具目录项（对应 dto/ToolKeyInfo.java） */
export interface ToolKeyInfo {
  key: string;
  name: string;
  description: string;
  /** builtin 内置 / custom 自定义 */
  type: string;
}

/** 工具开关记录（对应 model/ToolConfig.java） */
export interface ToolConfig {
  id?: number;
  scope: "PLATFORM" | "TENANT" | "USER";
  toolKey: string;
  enabled: number;
}

/** 技能视图项（对应 dto/SkillInfo.java） */
export interface SkillInfo {
  name: string;
  description?: string | null;
  enabled: boolean;
}

/** 工具集元数据（含工具详情列表，对应 ToolRegistry.ToolMetadataWithDetails） */
export interface ToolSetWithDetails {
  code: string;
  name: string;
  description: string;
  category: string;
  enabledByDefault: boolean;
  version: string;
  dependencies: string[];
  requiresHITL: boolean;
  allowedRoles: string[];
  /** 当前是否启用（运行时状态） */
  enabled: boolean;
  /** 该工具集包含的具体工具方法列表 */
  tools: ToolDetail[];
}

/** 具体工具方法详情（对应 ToolDetailExtractor.ToolDetail） */
export interface ToolDetail {
  name: string;
  description: string;
  returnType: string;
  parameters: ToolParameter[];
}

/** 工具方法参数（对应 ToolDetailExtractor.ToolParameter） */
export interface ToolParameter {
  name: string;
  type: string;
  description: string;
  required: boolean;
}

/** 工具分类中文映射 */
export const TOOL_CATEGORY_LABEL: Record<string, string> = {
  utility: "实用工具",
  search: "搜索工具",
  data: "数据处理",
  code: "代码相关",
  ai: "AI 增强",
  system: "系统管理",
};

/** 工具分类图标映射（Lucide 图标名） */
export const TOOL_CATEGORY_ICONS: Record<string, string> = {
  utility: "Wrench",
  search: "Search",
  data: "Database",
  code: "Code",
  ai: "Bot",
  system: "Settings",
};

/** Token 使用流水记录（对应后端 TokenUsageLog.java） */
export interface TokenUsageLog {
  id: number;
  userId: string;
  tenantId: number;
  username: string;
  sessionId?: string;
  provider: string;
  modelName: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  requestId?: string;
  toolName?: string;
  usageTime: string;
  usageDate?: string;
}

/** Token 使用汇总（对应后端 TokenUsageSummary.java） */
export interface TokenUsageSummary {
  id: number;
  userId: string;
  tenantId: number;
  username: string;
  periodType: string; // daily/monthly/yearly
  periodStart?: string; // YYYY-MM-DD
  periodEnd?: string;   // YYYY-MM-DD
  totalPromptTokens: number;
  totalCompletionTokens: number;
  totalTokens: number;
  requestCount: number;
  lastUpdateTime?: string;
}

/** 常用语/快捷指令（对应 model/UserQuickPhrase.java） */
export interface QuickPhrase {
  id?: number;
  tenantId?: number;
  userId?: string;
  title: string;
  content: string;
  sortOrder?: number;
  createTime?: string;
}

/** 定时任务（对应 model/ScheduledTask.java） */
export interface ScheduledTask {
  id?: number;
  tenantId?: number;
  userId?: string;
  username?: string;
  taskName: string;
  cronExpr: string;
  presetCode?: string | null;
  pipelineCode?: string | null;
  promptContent: string;
  notifyEmail?: string | null;
  enabled?: number;
  lastRunTime?: string | null;
  nextRunTime?: string | null;
  createTime?: string;
}

/** 定时任务执行日志（对应 model/ScheduledTaskLog.java） */
export interface ScheduledTaskLog {
  id: number;
  taskId: number;
  tenantId?: number;
  status: "SUCCESS" | "FAIL";
  resultText?: string | null;
  errorMsg?: string | null;
  runTime: string;
}

/** 预设模板市场项（对应 agent_preset 表 published=1 的记录） */
export interface MarketplacePreset extends AgentPreset {
  published: number;
  publishName?: string | null;
  publishDesc?: string | null;
  useCount: number;
  authorName?: string | null;
}

/** 用户渠道绑定（对应 sys_user_channel 表） */
export interface UserChannel {
  id?: number;
  userId?: string;
  channelType: string;
  channelUserId: string;
  channelUsername?: string | null;
  channelGroupId?: string | null;
  channelGroupName?: string | null;
  groupRole?: string | null;
  accessToken?: string | null;
  refreshToken?: string | null;
  status?: number;
  createTime?: string;
  updateTime?: string;
}
