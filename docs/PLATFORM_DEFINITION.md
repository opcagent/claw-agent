# Claw Agent 平台定义文档

> **版本**: 2.2  
> **更新日期**: 2026-09-02  
> **状态**: 生产就绪  
> **技术栈**: Spring Boot 3.5 + AgentScope Java 2.0 + Next.js 16 + MySQL 8 + Redis

---

## 📋 目录

1. [平台概述](#1-平台概述)
2. [核心特性](#2-核心特性)
3. [角色与权限体系](#3-角色与权限体系)
4. [作用域层级](#4-作用域层级)
5. [功能模块清单](#5-功能模块清单)
6. [技术架构](#6-技术架构)
7. [API 规范](#7-api-规范)
8. [数据库设计](#8-数据库设计)
9. [部署指南](#9-部署指南)

---

## 1. 平台概述

Claw Agent 是一个**企业级多租户 AI Agent 平台**,部署在用户自有服务器上,提供完整的 Agent 编排、工具管理、权限控制和数据统计能力。

### 1.1 核心价值

- ✅ **多租户隔离**: 租户 → 部门 → 用户三级组织架构,数据完全隔离
- ✅ **满血 AgentScope**: 工作区人格、分层记忆、上下文压缩、技能自学习、子 Agent、Plan Mode、Middleware 链
- ✅ **思考过程展示**: 支持展示模型推理过程（如 Claude extended thinking），前端可折叠查看
- ✅ **安全护栏**: Prompt Injection 防护 + 输出脱敏，GuardrailsMiddleware 保障安全
- ✅ **动态工具系统**: 注解扫描自动注册,运行时启用/禁用,细粒度权限控制
- ✅ **HITL 人工确认**: 敏感操作执行前弹窗审批,安全可控
- ✅ **Token 统计追踪**: 自动记录模型调用消耗,月度汇总,管理员视图
- ✅ **免费模型生态**: 内置 Ollama/Groq/HuggingFace 等零成本模型提供商
- ✅ **MCP 协议支持**: 集成 Git/GitHub/Chrome DevTools 等外部工具服务器

### 1.2 应用场景

| 场景 | 说明 |
|------|------|
| **企业内部知识库** | 员工通过自然语言查询公司文档、政策、流程 |
| **代码辅助开发** | 自动生成代码、审查 PR、正则测试、数据分析 |
| **客户服务自动化** | 智能客服机器人,自动回答常见问题 |
| **数据分析助手** | SQL 生成、报表解读、趋势分析 |
| **个人知识管理** | 笔记整理、文献综述、学习计划制定 |

---

## 2. 核心特性

### 2.1 Agent 能力矩阵

| 能力 | 说明 | 配置位置 |
|------|------|----------|
| **工作区人格** | `D:/claw-agent/.agentscope/workspace/AGENTS.md` 定义 Agent 人设与行为准则 | 文件系统 |
| **分层记忆** | 短期记忆(会话内) + 长期记忆(跨会话持久化) | `MemoryConfig` |
| **上下文压缩** | 消息数超阈值自动蒸馏摘要,保留关键信息 | `CompactionConfig` |
| **技能自学习** | Agent 可起草新技能(`propose_skill`),后台 curator 定期整理 | `SkillCuratorConfig` |
| **子 Agent 委派** | 复杂任务拆解为子任务,委派给 specialized subagents | `subagents/` 目录 |
| **Plan Mode** | 只读规划模式,输出执行计划后等待 HITL 审批退出 | 对话参数 |
| **Middleware 拦截** | 自定义中间件拦截模型调用、工具执行等事件 | `GuardrailsMiddleware` / `AgentTraceMiddleware` / `PerformanceMiddleware` |

**Middleware 链执行顺序**:
```
GuardrailsMiddleware (输入过滤 + 输出脱敏)
  → AgentTraceMiddleware (执行链路追踪)
    → PerformanceMiddleware (性能监控)
      → HarnessAgent 内置 Middleware
```

### 2.2 工具生态系统

#### 内置工具集

| 分类 | 工具集 | 功能描述 |
|------|--------|---------|
| **utility** | `system_tools` | 时间查询、日期计算、UUID 生成、系统信息 |
| **utility** | `math_tools` | 数学计算、哈希函数、Base64 编解码、密码生成 |
| **search** | `multi_search` | Tavily/Brave/Bing/SearXNG/DuckDuckGo 多级降级搜索 |
| **search** | `browser` | 浏览器自动化：网页浏览、标题获取、链接提取 |
| **data** | `note_tools` | 工作区文件读写、笔记管理、知识库维护 |

#### MCP 服务器 (平台级共享)

| 名称 | 传输协议 | 用途 | 状态 |
|------|---------|------|------|
| **Git MCP** | stdio | Git 仓库操作(克隆/提交/推送) | ✅ 已配置 |
| **GitHub MCP** | http | GitHub API 集成(Issue/PR/Actions) | ✅ 已配置 |
| **Chrome DevTools** | http | 浏览器自动化、网页截图、性能分析 | ✅ 已配置 |
| **SQLite MCP** | stdio | SQLite 数据库查询与管理 | ✅ 已配置 |
| **PostgreSQL MCP** | stdio | PostgreSQL 数据库连接与查询 | ✅ 已配置 |

#### Skills 技能库

| 技能名称 | 依赖 | 用途 |
|---------|------|------|
| **code-review** | ESLint/Pylint | 代码审查,检查规范性与潜在问题 |
| **data-analysis** | pandas/matplotlib | 数据分析,生成图表与洞察报告 |
| **regex-tester** | Python re 模块 | 正则表达式测试与优化 |

### 2.3 权限与安全

- ✅ **RBAC 角色模型**: 平台管理员 / 租户管理员 / 普通用户三级权限
- ✅ **菜单授权**: 后端按角色聚合菜单树,前端动态渲染导航栏
- ✅ **按钮级权限**: `hasAuthority('system:user:add')` 细粒度控制
- ✅ **HITL 人工确认**: 敏感工具执行前弹窗审批,支持允许/拒绝
- ✅ **审计日志**: 所有管理操作自动记录 `sys_oper_log`,含 IP/traceId
- ✅ **登录防护**: 失败计数限流,成功清零,防止暴力破解
- ✅ **安全护栏**: `GuardrailsMiddleware` 拦截 Prompt Injection 攻击，输出自动脱敏

**安全护栏功能**:
- **输入过滤**: 检测 10 种 Prompt Injection 模式（中英文），命中时拦截并返回警告
- **输出脱敏**: 自动检测并替换文件路径、内网 IP、API Key 等敏感信息
- **Middleware 链**: 作为第一个 Middleware 执行，确保所有输入输出都经过检查

---

## 3. 角色与权限体系

### 3.1 三层角色定义

| 角色 | role_key | Spring Security 权限 | 权限范围 | 数据隔离 |
|------|----------|---------------------|---------|---------|
| **平台管理员** | `admin` | `ROLE_ADMIN` | 全局最高权限,管理所有租户 | 无限制(跨租户) |
| **租户管理员** | `tenant_admin` | `ROLE_TENANT_ADMIN` | 本租户内管理用户/角色/菜单 | 仅限本租户 |
| **普通用户** | `common` | `ROLE_COMMON` | 本租户业务功能(聊天/工具) | 仅限本租户 |

### 3.2 角色职责矩阵

| 功能模块 | 平台管理员 | 租户管理员 | 普通用户 |
|---------|-----------|-----------|---------|
| **租户管理** | ✅ 创建/删除/分配管理员 | ❌ |  |
| **用户管理** | ✅ 全平台用户 | ✅ 本租户用户 | ❌ |
| **角色管理** | ✅ 全平台角色 | ✅ 本租户角色授权 | ❌ |
| **菜单管理** | ✅ 全平台菜单 | ✅ 本租户菜单授权 | ❌ |
| **模型配置** | ✅ 平台级配置 | ✅ 租户级覆盖 | ❌ |
| **工具管理** | ✅ 启用/禁用工具集 | ✅ 查看可用工具 | ✅ 使用已启用工具 |
| **Token 统计** | ✅ 全平台汇总 | ✅ 本租户排行 | ✅ 个人使用明细 |
| **聊天对话** | ✅ | ✅ | ✅ |
| **预设模板** | ✅ 平台预设 | ✅ 租户预设 | ✅ 个人预设 |

### 3.3 权限判断逻辑

**后端 (LoginUser.java)**:
```java
public boolean isAdmin() {
    return roleKeys != null && roleKeys.contains("admin");
}

public boolean isTenantAdmin() {
    return isAdmin() || (roleKeys != null && roleKeys.contains("tenant_admin"));
}
```

**前端 (auth.ts)**:
```typescript
isAdmin: () => get().user?.roles?.includes("admin") ?? false,
isTenantAdmin: () => {
  const roles = get().user?.roles ?? [];
  return roles.includes("admin") || roles.includes("tenant_admin");
},
```

**JWT 角色映射 (JwtAuthFilter.java)**:
```java
// 角色键 → ROLE_ 前缀权限
"admin"        → "ROLE_ADMIN"
"tenant_admin" → "ROLE_TENANT_ADMIN"  
"common"       → "ROLE_COMMON"
```

---

## 4. 作用域层级

### 4.1 三级作用域

```
┌─────────────────────────────────────────┐
│         PLATFORM (平台级)                │
│  scope = "PLATFORM"                      │
│  - 模型提供商配置                        │
│  - MCP 服务器配置                        │
│  - Skills 技能库                         │
│  - 系统级菜单/字典                       │
│  - 平台预设模板                          │
│  - 租户管理                              │
└─────────────────────────────────────────
              ↓ (就近覆盖)
─────────────────────────────────────────┐
│          TENANT (租户级)                 │
│  scope = "TENANT_<tenant_id>"            │
│  - 租户内用户列表                        │
│  - 租户内角色分配                        │
│  - 租户内会话/聊天                       │
│  - 租户内 Token 使用统计                 │
│  - 租户级模型配置覆盖                    │
│  - 租户预设模板                          │
│  - 租户级工具集启用状态                  │
─────────────────────────────────────────┘
              ↓ (就近覆盖)
┌─────────────────────────────────────────┐
│           USER (用户级)                  │
│  scope = "USER_<user_id>"                │
│  - 个人偏好设置                          │
│  - 个人工作区                            │
│  - 个人笔记/知识                         │
│  - 个人 Token 使用统计                   │
│  - 个人预设模板                          │
└─────────────────────────────────────────┘
```

### 4.2 配置解析优先级

```java
// ConfigService.resolveConfig() 伪代码
T resolveConfig(String key, Class<T> type, Long tenantId, Long userId) {
    // 1. 用户级配置 (最高优先级)
    T userConfig = queryByScope("USER_" + userId, key);
    if (userConfig != null) return userConfig;
    
    // 2. 租户级配置
    T tenantConfig = queryByScope("TENANT_" + tenantId, key);
    if (tenantConfig != null) return tenantConfig;
    
    // 3. 平台级配置 (最低优先级,默认值)
    T platformConfig = queryByScope("PLATFORM", key);
    return platformConfig != null ? platformConfig : defaultValue;
}
```

---

## 5. 功能模块清单

### 5.1 认证与用户管理

#### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|---------|
| POST | `/api/auth/login` | 用户登录,返回 JWT | 匿名 |
| PUT | `/api/auth/password` | 修改本人密码 | 已登录 |
| GET | `/api/auth/info` | 获取当前用户信息 | 已登录 |
| GET | `/api/auth/menus` | 获取可见菜单树 | 已登录 |
| GET | `/api/auth/profile` | 获取个人资料详情 | 已登录 |
| PUT | `/api/auth/profile` | 更新个人资料 | 已登录 |
| GET | `/api/auth/login-logs` | 最近登录记录 | 已登录 |
| POST | `/api/auth/logout` | 登出(记录日志) | 已登录 |

#### 前端页面

- `/login` - 登录页
- `/register` - 注册页(可选,默认关闭)
- `/profile` - 个人中心

### 5.2 租户管理 (平台管理员专属)

#### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|---------|
| GET | `/api/admin/tenant/list` | 全部租户列表 | `ADMIN` |
| POST | `/api/admin/tenant` | 新增租户(传统方式) | `ADMIN` |
| POST | `/api/admin/tenant/with-admin` | 新增租户+初始管理员 | `ADMIN` |
| PUT | `/api/admin/tenant/{id}` | 修改租户信息 | `ADMIN` |
| DELETE | `/api/admin/tenant/{id}` | 删除租户 | `ADMIN` |
| PUT | `/api/admin/tenant/{id}/admin` | 设置租户管理员 | `ADMIN` |

#### 前端页面

- `/system/tenant` - 租户管理页
  - 租户列表表格
  - 新增/编辑租户对话框
  - 设置管理员抽屉(加载租户用户列表,单选确认)

### 5.3 用户管理 (租户级)

#### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|---------|
| GET | `/api/admin/user/list` | 本租户用户列表 | `ADMIN` 或 `TENANT_ADMIN` |
| GET | `/api/admin/user/page` | 分页用户列表 | `ADMIN` 或 `TENANT_ADMIN` |
| POST | `/api/admin/user` | 新增用户 | `ADMIN` 或 `TENANT_ADMIN` + `system:user:add` |
| PUT | `/api/admin/user/{id}` | 修改用户信息 | `ADMIN` 或 `TENANT_ADMIN` + `system:user:edit` |
| PUT | `/api/admin/user/{id}/password` | 重置密码 | `ADMIN` 或 `TENANT_ADMIN` + `system:user:resetPwd` |
| DELETE | `/api/admin/user/{id}` | 删除用户 | `ADMIN` 或 `TENANT_ADMIN` + `system:user:remove` |
| GET | `/api/admin/user/{id}/roles` | 获取用户角色 | `ADMIN` 或 `TENANT_ADMIN` |
| PUT | `/api/admin/user/{id}/roles` | 分配用户角色 | `ADMIN` 或 `TENANT_ADMIN` + `system:user:grant` |

#### 前端页面

- `/system/user` - 用户管理页
  - 用户列表表格(支持搜索/分页)
  - 新增用户对话框
  - 分配角色抽屉(加载本租户角色列表,多选确认)

### 5.4 角色与权限管理

#### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|---------|
| GET | `/api/admin/role/list` | 角色列表 | `ADMIN` 或 `TENANT_ADMIN` |
| POST | `/api/admin/role` | 新增角色 | `ADMIN` 或 `TENANT_ADMIN` |
| PUT | `/api/admin/role/{id}` | 修改角色 | `ADMIN` 或 `TENANT_ADMIN` |
| DELETE | `/api/admin/role/{id}` | 删除角色 | `ADMIN` 或 `TENANT_ADMIN` |
| GET | `/api/admin/role/{id}/menus` | 获取角色菜单 | `ADMIN` 或 `TENANT_ADMIN` |
| PUT | `/api/admin/role/{id}/menus` | 分配角色菜单 | `ADMIN` 或 `TENANT_ADMIN` |

#### 前端页面

- `/system/role` - 角色管理页
  - 角色列表表格
  - 新增/编辑角色对话框
  - 分配菜单树形勾选框

### 5.5 菜单管理

#### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|---------|
| GET | `/api/admin/menu/tree` | 菜单树 | `ADMIN` 或 `TENANT_ADMIN` |
| POST | `/api/admin/menu` | 新增菜单 | `ADMIN` |
| PUT | `/api/admin/menu/{id}` | 修改菜单 | `ADMIN` |
| DELETE | `/api/admin/menu/{id}` | 删除菜单 | `ADMIN` |

#### 菜单结构

```
AI 工作台 (M, /home)
├── 聊天对话 (C, /home)
├── 预设模板 (C, /presets)
── 流水线编排 (C, /pipelines)
├── 定时任务 (C, /scheduled-tasks)
└── 模板市场 (C, /marketplace)

平台治理 (M, /system)
├── 成员与账户 (C, /system/user)
├── 角色与权限 (C, /system/role)
├── 菜单权限 (C, /system/menu)
├── 组织架构 (C, /system/dept)
├── 租户空间 (C, /system/tenant) - 仅 admin
├── 渠道管理 (C, /system/channels)
├── 邮件配置 (C, /system/email-config)
├── 审计日志 (C, /system/log)
├── 数据字典 (C, /system/dict)
├── 在线监控 (C, /system/online)
── Token 统计 (C, /token-usage)
```

### 5.6 智能对话

#### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|---------|
| POST | `/api/chat/stream` | SSE 流式对话 | 已登录 |
| POST | `/api/chat/confirm` | HITL 确认工具执行 | 已登录 |

#### 请求格式

```json
{
  "sessionId": "uuid-optional",
  "content": "用户消息内容",
  "attachments": ["base64-encoded-image"],
  "presetCode": "preset-code-optional",
  "pipelineCode": "pipeline-code-optional"
}
```

#### SSE 事件类型

| 事件类型 | 说明 | 数据格式 |
|---------|------|---------|
| `start` | 对话开始 | `{ replyId: string }` |
| `text` | 文本增量 | `{ delta: string }` |
| `tool_start` | 工具调用开始 | `{ toolCallId, toolName }` |
| `tool_end` | 工具调用结束 | `{ toolCallId, state }` |
| `confirm_request` | HITL 确认请求 | `{ pendingToolCalls[] }` |
| `subagent` | 子 Agent 暴露 | `{ subagentId, label }` |
| `end` | 对话结束 | `{ replyId }` |
| `error` | 错误事件 | `{ message }` |

#### 前端页面

- `/chat` - 聊天主页
  - 左侧会话列表
  - 中间消息区域(SSE 流式渲染)
  - 右侧工具调用折叠面板
  - 顶部预设人格选择器

### 5.7 工具管理

#### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|---------|
| GET | `/api/tools/list` | 所有工具集列表 | 已登录 |
| GET | `/api/tools/list-with-details` | 工具集+具体工具详情 | 已登录 |
| GET | `/api/tools/category/{category}` | 按分类查询工具集 | 已登录 |
| GET | `/api/tools/{code}/tools` | 获取工具集内具体工具 | 已登录 |
| POST | `/api/tools/{code}/enable` | 启用工具集 | `ADMIN` 或 `TENANT_ADMIN` |
| POST | `/api/tools/{code}/disable` | 禁用工具集 | `ADMIN` 或 `TENANT_ADMIN` |

#### 工具分类体系

| 分类代码 | 中文名称 | 示例工具集 |
|---------|---------|-----------|
| `utility` | 实用工具 | system_tools, math_tools |
| `search` | 搜索工具 | multi_search |
| `data` | 数据处理 | note_tools |
| `code` | 代码相关 | (待扩展) |
| `ai` | AI 增强 | (待扩展) |
| `system` | 系统管理 | (待扩展) |

### 5.8 Token 使用统计

#### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|---------|
| GET | `/api/token-usage/current-month` | 本月汇总 | 已登录 |
| GET | `/api/token-usage/month/{year}/{month}` | 指定月份汇总 | 已登录 |
| GET | `/api/token-usage/recent-months?months=6` | 最近 N 个月趋势 | 已登录 |
| GET | `/api/token-usage/logs?limit=50` | 使用流水明细 | 已登录 |
| GET | `/api/token-usage/admin/tenant-users` | 租户用户排行 | `ADMIN` 或 `TENANT_ADMIN` |
| POST | `/api/token-usage/test-record` | 测试接口(手动记录) | 已登录 |

#### 数据库设计

**token_usage_log** (流水表):
```sql
CREATE TABLE token_usage_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  session_id VARCHAR(64) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  model_name VARCHAR(64) NOT NULL,
  prompt_tokens INT NOT NULL DEFAULT 0,
  completion_tokens INT NOT NULL DEFAULT 0,
  total_tokens INT NOT NULL DEFAULT 0,
  request_id VARCHAR(64),
  tool_name VARCHAR(64),
  usage_time DATETIME NOT NULL,
  INDEX idx_user_date (user_id, usage_date),
  INDEX idx_session_model (session_id, model_name)
);
```

**token_usage_summary** (汇总表):
```sql
CREATE TABLE token_usage_summary (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  period_type VARCHAR(16) NOT NULL COMMENT 'daily/monthly/yearly',
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  total_prompt_tokens INT NOT NULL DEFAULT 0,
  total_completion_tokens INT NOT NULL DEFAULT 0,
  total_tokens INT NOT NULL DEFAULT 0,
  request_count INT NOT NULL DEFAULT 0,
  last_update_time DATETIME,
  UNIQUE KEY uk_user_period (user_id, period_type, period_start)
);
```

**触发器**: `trg_update_token_summary_after_insert` - 插入流水时自动更新汇总表

#### 前端页面

- `/token-usage` - Token 统计页
  - 本月汇总卡片(总 Token/请求次数/周期/更新时间)
  - Tab 1: 使用趋势(近 6 个月柱状图 + 提供商占比饼图)
  - Tab 2: 使用流水(最近 50 条记录表格)
  - Tab 3: 管理员视图(租户用户排行,仅 admin/tenant_admin 可见)

### 5.9 模型提供商配置

#### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|---------|
| GET | `/api/config/model-providers` | 获取模型提供商列表 | `ADMIN` 或 `TENANT_ADMIN` |
| POST | `/api/config/model-providers` | 新增提供商配置 | `ADMIN` |
| PUT | `/api/config/model-providers/{id}` | 修改提供商配置 | `ADMIN` |
| DELETE | `/api/config/model-providers/{id}` | 删除提供商配置 | `ADMIN` |
| POST | `/api/config/model-providers/{id}/test` | 测试连接 | `ADMIN` |

#### 支持的模型提供商

| 提供商 | 类型 | 默认模型 | 特点 |
|-------|------|---------|------|
| **dashscope** | DashScope | qwen-plus | 阿里云通义千问 |
| **deepseek** | OpenAI 兼容 | deepseek-chat | DeepSeek 官方 |
| **openai** | OpenAI 兼容 | gpt-4.1-mini | OpenAI/Kimi/vLLM |
| **ollama** | Ollama | qwen2.5:7b | 本地运行,零成本 |
| **groq** | OpenAI 兼容 | llama3.1-70b | 极速推理 |
| **huggingface** | OpenAI 兼容 | mistral-7b | HuggingFace Hub |

### 5.10 MCP 服务器管理

#### API 端点

| 方法 | 路径 | 说明 | 权限要求 |
|------|------|------|---------|
| GET | `/api/config/mcp-servers` | 获取 MCP 服务器列表 | `ADMIN` 或 `TENANT_ADMIN` |
| POST | `/api/config/mcp-servers` | 新增 MCP 服务器 | `ADMIN` |
| PUT | `/api/config/mcp-servers/{id}` | 修改 MCP 服务器 | `ADMIN` |
| DELETE | `/api/config/mcp-servers/{id}` | 删除 MCP 服务器 | `ADMIN` |
| POST | `/api/config/mcp-servers/{id}/test` | 测试连接 | `ADMIN` |

#### 已配置的免费 MCP 服务器

| 名称 | 传输协议 | 端点 | 用途 |
|------|---------|------|------|
| Git MCP | stdio | `npx -y @modelcontextprotocol/server-git` | Git 仓库操作 |
| GitHub MCP | http | `https://api.github.com` | GitHub API 集成 |
| Chrome DevTools | http | `http://localhost:9222` | 浏览器自动化 |
| SQLite MCP | stdio | `npx -y @modelcontextprotocol/server-sqlite` | SQLite 数据库 |
| PostgreSQL MCP | stdio | `npx -y @modelcontextprotocol/server-postgres` | PostgreSQL 数据库 |

---

## 6. 技术架构

### 6.1 后端架构

```
──────────────────────────────────────────────────────────────┐
│  Spring Boot 3.5 (WebFlux 响应式)                             │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Controller Layer (协议转换,参数校验)                     │  │
│  │ - AuthController, UserController, TenantController      │  │
│  │ - ChatController, ToolController, ConfigController      │  │
│  │ - TokenUsageController                                  │  │
│  └──────────────────────┬─────────────────────────────────┘  │
│                         ↓                                    │
│  ┌────────────────────────────────────────────────────────  │
│  │ Service Layer (业务逻辑)                                 │  │
│  │ - AuthService, UserService, TenantService               │  │
│  │ - AgentService, ConfigService, TokenUsageService        │  │
│  │ - CapabilityService (能力解析:模型/MCP/工具)             │  │
│  └──────────────────────┬─────────────────────────────────┘  │
│                         ↓                                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Mapper Layer (MyBatis Plus)                             │  │
│  │ - UserMapper, RoleMapper, MenuMapper                    │  │
│  │ - TokenUsageLogMapper, TokenUsageSummaryMapper          │  │
│  │ - Custom SQL in resources/mapper/*.xml                  │  │
│  └──────────────────────┬─────────────────────────────────┘  │
│                         ↓                                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ AgentScope Integration                                  │  │
│  │ - AgentRegistry (HarnessAgent 单例管理)                 │  │
│  │ - ModelFactory (模型构建工厂)                           │  │
│  │ - ToolRegistry (工具自动扫描注册)                       │  │
│  │ - RedisAgentStateStore (会话状态存储)                   │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 前端架构

```
┌──────────────────────────────────────────────────────────────┐
│  Next.js 16 (App Router) + React 19 + TypeScript              │
│                                                              │
│  ┌────────────────────────────────────────────────────────  │
│  │ Pages (App Router)                                      │  │
│  │ - /login, /chat, /system/*, /token-usage                │  │
│  └──────────────────────┬─────────────────────────────────┘  │
│                         ↓                                    │
│  ┌────────────────────────────────────────────────────────  │
│  │ Components                                              │  │
│  │ - AppShell (顶部导航 + 左侧侧边栏)                       │  │
│  │ - ChatView (SSE 流式渲染)                               │  │
│  │ - UI Primitives (Base UI/shadcn)                        │  │
│  └──────────────────────┬─────────────────────────────────┘  │
│                         ↓                                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ State Management                                        │  │
│  │ - Zustand (auth store, chat store)                      │  │
│  │ - React Context (Tabs, Dialogs)                         │  │
│  └──────────────────────┬─────────────────────────────────┘  │
│                         ↓                                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ API Client (lib/api.ts)                                 │  │
│  │ - JWT 注入                                              │  │
│  │ - 错误码处理                                            │  │
│  │ - 401 自动跳登录                                         │  │
│  │ - SSE 流解析 (fetch + ReadableStream)                   │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### 6.3 数据流

```
用户操作
  ↓
前端 API 调用 (lib/api.ts)
  ↓
HTTP Request (Authorization: Bearer <JWT>)
  ↓
JwtAuthFilter (解析 JWT → LoginUser)
  ↓
Controller (@PreAuthorize 鉴权)
  ↓
Service (业务逻辑 + 租户隔离)
  ↓
Mapper (MyBatis Plus)
  ↓
MySQL / Redis
  ↓
Response (Result<T> / SSE Stream)
  ↓
前端渲染 / 状态更新
```

---

## 7. API 规范

### 7.1 通用约定

**响应格式**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

**错误码**:
| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录或 token 过期 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

**时间格式**: `yyyy-MM-dd HH:mm:ss` (UTC+8)

### 7.2 认证相关

**登录**:
```bash
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}

Response:
{
  "code": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "username": "admin",
    "nickname": "平台管理员",
    "tenantId": 1,
    "tenantName": "默认租户",
    "roles": ["admin"],
    "permissions": ["*:*:*"]
  }
}
```

### 7.3 聊天相关

**流式对话**:
```
POST /api/chat/stream
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream

{
  "sessionId": "uuid-optional",
  "content": "你好,请介绍一下自己",
  "attachments": [],
  "presetCode": null,
  "pipelineCode": null
}

Response (SSE):
event: start
data: {"replyId":"abc123"}

event: text
data: {"delta":"你好!我是 Claw,"}

event: text
data: {"delta":"一名个人全能助理..."}

event: end
data: {"replyId":"abc123"}
```

---

## 8. 数据库设计

### 8.1 核心表清单

| 表名 | 说明 | Flyway 版本 |
|------|------|------------|
| `sys_tenant` | 租户表 | V3 |
| `sys_dept` | 部门表 | V4 |
| `sys_user` | 用户表 | V1 |
| `sys_role` | 角色表 | V4 |
| `sys_menu` | 菜单表 | V4 |
| `sys_user_role` | 用户-角色关联 | V4 |
| `sys_role_menu` | 角色-菜单关联 | V4 |
| `sys_dict_type` | 字典类型 | V5 |
| `sys_dict_data` | 字典数据 | V5 |
| `sys_oper_log` | 操作日志 | V4 |
| `sys_login_log` | 登录日志 | V1 |
| `agent_preset` | Agent 预设模板 | V5 |
| `prompt_template` | 提示词模板 | V6 |
| `agent_pipeline` | 流水线剧本 | V6 |
| `model_provider_config` | 模型提供商配置 | V3 |
| `mcp_server` | MCP 服务器配置 | V33 |
| `token_usage_log` | Token 使用流水 | V32 |
| `token_usage_summary` | Token 使用汇总 | V32 |

### 8.2 Flyway 迁移脚本

| 版本 | 文件名 | 说明 |
|------|--------|------|
| V1 | `V1__init_schema.sql` | 初始化基础表结构 |
| V2 | `V2__init_data.sql` | 初始化默认数据(admin 用户/角色) |
| V3 | `V3__multi_tenant_config.sql` | 多租户与配置表 |
| V4 | `V4__rbac_tables.sql` | RBAC 权限表 |
| V5 | `V5__dict_and_agent_preset.sql` | 字典与预设模板 |
| V6 | `V6__pipeline_and_template.sql` | 流水线与提示词模板 |
| V32 | `V32__token_usage_tracking.sql` | Token 统计系统 |
| V33 | `V33__add_free_mcp_servers.sql` | 免费 MCP 服务器配置 |
| V34 | `V34__token_usage_menu.sql` | Token 统计菜单 |
| V35 | `V35__add_free_model_providers.sql` | 免费模型提供商配置 |

---

## 9. 部署指南

### 9.1 环境要求

- **JDK**: 17+ (推荐 21)
- **Node.js**: 18+ (前端开发)
- **MySQL**: 8.0+
- **Redis**: 6.0+ (可选,单机开发可降级为 JSON 文件)
- **Maven**: 3.8+

### 9.2 后端部署

```
# 1. 编译打包
cd backend
mvn clean package -DskipTests

# 2. 启动服务
java -jar target/backend-1.0.0-SNAPSHOT.jar

# 3. 健康检查
curl http://localhost:8080/actuator/health
```

**配置文件** (`application.yml`):
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/claw_agent?useUnicode=true&characterEncoding=UTF-8
    username: root
    password: root
  
  data:
    redis:
      host: localhost
      port: 6379

claw:
  agent:
    state-store-type: redis  # redis / json
    workspace-root: D:/claw-agent/.agentscope/workspace
  jwt:
    secret: your-secret-key-change-in-production
    expiration: 86400000  # 24h
```

### 9.3 前端部署

**开发环境**:
```bash
cd frontend
npm install
npm run dev  # http://localhost:3000
```

**生产环境**:
```bash
npm run build
npm run start  # http://localhost:3000
```

**环境变量** (`.env.local`):
```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

### 9.4 Docker 部署 (可选)

**docker-compose.yml**:
```
version: '3.8'

services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: claw_agent
    volumes:
      - mysql-data:/var/lib/mysql
    ports:
      - "3306:3306"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  backend:
    build: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: mysql
      REDIS_HOST: redis
    ports:
      - "8080:8080"
    depends_on:
      - mysql
      - redis

  frontend:
    build: ./frontend
    environment:
      NEXT_PUBLIC_API_URL: http://localhost:8080
    ports:
      - "3000:3000"
    depends_on:
      - backend

volumes:
  mysql-data:
```

---

## 10. 常见问题 (FAQ)

### Q1: 如何添加新的工具集?

**A**: 只需三步:
1. 创建工具类并添加 `@ToolSet` 注解
2. 在类中编写 `@Tool` 注解的方法
3. 重启应用,自动扫描注册

示例:
```java
@ToolSet(
    code = "weather_tools",
    name = "天气工具",
    description = "查询天气预报",
    category = "utility",
    enabledByDefault = true
)
@Component
public class WeatherTools {
    
    @Tool(name = "get_weather", description = "获取城市天气")
    public String getWeather(@ToolParam("城市名称") String city) {
        // 实现逻辑
        return "晴天,25°C";
    }
}
```

### Q2: 如何实现自动 Token 记录?

**A**: 当前版本需手动调用 `TokenUsageService.recordUsage()`,后续将实现 Middleware 自动拦截。临时方案:
1. 在 `AgentService.doChat()` 的 `end` 事件中提取 usage
2. 调用 `tokenUsageService.recordUsage(...)`

### Q3: 如何配置免费模型?

**A**: 访问 **平台治理 → 模型提供商**,点击"新增":
- **Ollama**: `provider=ollama`, `baseUrl=http://localhost:11434/v1`, `apiKey=null`
- **Groq**: `provider=openai`, `baseUrl=https://api.groq.com/openai/v1`, `apiKey=<your-key>`

详细配置见 [FREE_MODEL_PROVIDERS.md](./FREE_MODEL_PROVIDERS.md)

### Q4: 租户管理员能看到其他租户的数据吗?

**A**: **不能**。所有 Service 层查询都强制加上 `.eq(TenantId, current.getTenantId())`,确保数据隔离。平台管理员(`admin`)除外,可跨租户查询。

### Q5: 如何备份与恢复数据?

**A**: 
```bash
# 备份
mysqldump -u root -p claw_agent > backup_$(date +%Y%m%d).sql

# 恢复
mysql -u root -p claw_agent < backup_20260827.sql
```

---

## 11. 附录

### 11.1 术语表

| 术语 | 说明 |
|------|------|
| **HITL** | Human-In-The-Loop,人工介入确认 |
| **MCP** | Model Context Protocol,模型上下文协议 |
| **SSE** | Server-Sent Events,服务器推送事件 |
| **RBAC** | Role-Based Access Control,基于角色的访问控制 |
| **Flyway** | 数据库迁移工具 |
| **Reactor** | Spring WebFlux 响应式编程框架 |

### 11.2 参考文档

- [AgentScope Java 官方文档](https://agentscope.io/)
- [Spring Boot 3.5 文档](https://spring.io/projects/spring-boot)
- [Next.js 16 文档](https://nextjs.org/docs)
- [MyBatis Plus 文档](https://baomidou.com/)
- [动态工具注册系统](./DYNAMIC_TOOL_REGISTRY.md)
- [Token 使用统计系统](./TOKEN_USAGE_TRACKING.md)
- [免费 MCP 与 Skills 平台](./FREE_MCP_AND_SKILLS_PLATFORM.md)

---

## 12. 更新日志

### v2.1 (2026-09-01)
- ✅ 新增会话归档功能（V49 迁移，归档/恢复/删除）
- ✅ 新增渠道管理功能（Webhook 接收、适配器模式）
- ✅ 新增邮件配置功能（SMTP 配置、三级作用域）
- ✅ 新增定时任务功能（CRUD + 启停 + 日志 + 邮件通知）
- ✅ 新增预设模板市场（浏览、使用、搜索）
- ✅ 新增流水线编排（外贸客户开发 7 步模板）
- ✅ 权限模式动态化（write_file/delete_note 跟随 permission_mode）
- ✅ 聊天输入框优化（Textarea 自动高度、/ 快捷键触发快捷指令）
- ✅ 表格预览/导出（Markdown 表格检测、CSV 导出、全屏预览）
- ✅ 菜单结构更新（新增渠道/邮件/定时任务/市场等入口）

### v2.0 (2026-08-27)
- ✅ 完成多租户架构与 RBAC 权限系统
- ✅ 实现动态工具注册系统(注解扫描)
- ✅ 新增 Token 使用统计功能(流水+汇总+管理员视图)
- ✅ 集成 5+ 免费 MCP 服务器与模型提供商
- ✅ 完善前端页面(租户管理/用户管理/Token 统计)
- ✅ 修复角色判断大小写不匹配问题
- ✅ 修复 TokenUsageController 权限缺失问题

### v1.2 (2026-08)
- ✅ 基础认证与用户体系
- ✅ 智能对话(SSE 流式)
- ✅ 预设模板与流水线
- ✅ 初步权限控制

---

**文档维护者**: Claw Agent Team  
**反馈渠道**: GitHub Issues  
**许可证**: MIT
