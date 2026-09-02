# Claw Agent 平台定位与能力清单

> **版本**: 2.2  
> **更新日期**: 2026-09-02  
> **定位**: 个人/小团队私有化部署的 AI Agent 平台  
> **核心特点**: 单实例多租户 + AgentScope 满血版 + 动态工具系统 + 流水线编排 + 安全护栏

---

## 🎯 平台真实定位

### 核心理念

Claw Agent 不是企业级 SaaS 平台,而是**面向个人开发者和小团队的私有化 AI Agent 运行环境**:

1. **单实例架构**: 一个 Spring Boot 服务服务所有用户,按 `(userId, sessionId)` 隔离会话
2. **私有化部署**: 部署在用户自己的服务器/Docker,数据完全自主可控
3. **零成本启动**: 内置 Ollama/Groq 等免费模型,无需 API Key 即可使用
4. **AgentScope 满血版**: 完整接入工作区、记忆、压缩、技能、子 Agent、HITL 等高级能力

### 目标用户

| 用户类型 | 典型场景 | 核心价值 |
|---------|---------|---------|
| **个人开发者** | 本地知识库助手、代码辅助、学习笔记整理 | 隐私安全、零成本、可定制 |
| **小团队(5-50人)** | 内部文档检索、客服机器人、数据分析 | 数据隔离、权限控制、成本可控 |
| **技术爱好者** | 探索 Agent 能力、测试新工具/MCP | 开箱即用、扩展性强 |

### ❌ 不是什么

- ❌ **不是企业级 SaaS**: 不支持大规模并发(万级用户)、没有 SLA 保证
- ❌ **不是通用聊天机器人**: 专注于 Agent 编排和工具调用,而非简单问答
- ❌ **不是低代码平台**: 需要一定的技术能力部署和配置
- ❌ **不是多租户商业化平台**: 虽然有租户概念,但主要为了数据隔离,非计费单元

---

## ✅ 已实现的核心能力

### 1. 多租户与权限体系 (成熟度: ⭐⭐⭐⭐⭐)

**实际实现**:
- ✅ 三级角色: `admin` (平台管理员) / `tenant_admin` (租户管理员) / `common` (普通用户)
- ✅ RBAC 菜单授权: 后端按角色聚合菜单树,前端动态渲染
- ✅ 租户数据隔离: 所有查询强制 `.eq(tenantId, current.getTenantId())`
- ✅ 平台管理员专属功能: 创建/删除租户、分配租户管理员

**API 端点**:
```
POST   /api/auth/login              # 登录
GET    /api/auth/menus              # 获取菜单树
GET    /api/admin/tenant/list       # 租户列表 (仅 admin)
PUT    /api/admin/tenant/{id}/admin # 设置租户管理员 (仅 admin)
GET    /api/admin/user/list         # 用户列表 (admin + tenant_admin)
PUT    /api/admin/user/{id}/roles   # 分配用户角色
```

**前端页面**:
- `/login` - 登录页
- `/system/tenant` - 租户管理 (仅 admin 可见)
- `/system/user` - 用户管理 (admin + tenant_admin)
- `/system/role` - 角色管理
- `/system/menu` - 菜单管理

### 2. 智能对话系统 (成熟度: ⭐⭐⭐⭐⭐)

**实际实现**:
- ✅ SSE 流式输出: `POST /api/chat/stream` 返回 Server-Sent Events
- ✅ 多模态支持: Base64 图片附件直接投喂模型
- ✅ 预设人格叠加: `presetCode` 参数注入模板提示词
- ✅ HITL 人工确认: 敏感工具执行前弹窗审批 (`/api/chat/confirm`)
- ✅ 会话历史持久化: 自动保存聊天记录到 `chat_session` / `chat_message`
- ✅ 会话归档: 支持将会话归档隐藏，可在「归档」Tab 查看/恢复/删除
- ✅ 表格预览/导出: 自动检测 Markdown 表格，支持 CSV 导出和全屏预览
- ✅ 快捷指令面板: `/` 快捷键或点击按钮触发，支持常用语快速插入
- ✅ 输入框优化: Textarea 自动高度调整（1-8 行），Enter 发送/Shift+Enter 换行

**SSE 事件类型**:
```typescript
type ChatEventType = 
  | 'start'           // 对话开始
  | 'thinking_start'  // 思考过程开始（模型推理）
  | 'thinking'        // 思考过程增量文本
  | 'thinking_end'    // 思考过程结束
  | 'text'            // 文本增量
  | 'tool_start'      // 工具调用开始
  | 'tool_end'        // 工具调用结束
  | 'confirm_request' // HITL 确认请求
  | 'subagent'        // 子 Agent 暴露
  | 'end'             // 对话结束
  | 'error';          // 错误事件
```

**思考过程展示**:
- ✅ 支持展示模型推理过程（如 Claude extended thinking）
- ✅ 前端可折叠展示思考文本，思考中显示旋转动画
- ✅ 思考结束后可手动展开查看推理过程

**前端页面**:
- `/home` - 聊天主页 (SSE 流式渲染 + 工具折叠面板 + 会话归档 + 表格预览)

### 3. 动态工具注册系统 (成熟度: ⭐⭐⭐⭐⭐)

**实际实现**:
- ✅ 注解扫描自动注册: `@ToolSet` 定义工具集元数据
- ✅ 运行时启用/禁用: `/api/tools/{code}/enable` / `/disable`
- ✅ 工具详情提取: 反射扫描 `@Tool` 方法,生成结构化描述
- ✅ 6 大分类体系: utility / search / data / code / ai / system

**已内置工具集**:
| 工具集 | 分类 | 功能 |
|-------|------|------|
| `system_tools` | utility | 时间查询、日期计算、UUID 生成 |
| `math_tools` | utility | 数学计算、哈希、Base64、密码生成 |
| `multi_search` | search | Tavily/Brave/Bing/SearXNG/DuckDuckGo 多级降级搜索 |
| `browser` | search | 浏览器自动化：网页浏览、标题获取、链接提取 |
| `note_tools` | data | 工作区文件读写、笔记管理 |

**API 端点**:
```
GET    /api/tools/list                # 所有工具集列表
GET    /api/tools/list-with-details   # 工具集+具体工具详情
GET    /api/tools/category/{category} # 按分类查询
POST   /api/tools/{code}/enable       # 启用工具集
POST   /api/tools/{code}/disable      # 禁用工具集
```

**技术亮点**:
- 零侵入扩展: 新增工具只需添加 `@ToolSet` 注解,无需修改核心代码
- 平台感知: REST API 暴露完整工具目录,前端可动态展示
- 细粒度权限: 支持工具级 `hasAuthority('system:user:add')` 控制

### 4. Token 使用统计 (成熟度: ⭐⭐⭐⭐⭐)

**实际实现**:
- ✅ 流水记录表: `token_usage_log` 记录每次模型调用
- ✅ 月度汇总表: `token_usage_summary` 按月聚合统计
- ✅ 数据库触发器: 插入流水时自动更新汇总 (`trg_update_token_summary_after_insert`)
- ✅ **自动记录**: 通过 `ModelCallEndEvent` 事件驱动，模型调用时自动提取 `ChatUsage` 异步落库
- ✅ 管理员视图: 租户内用户 Token 使用排行

**技术实现**:
- 事件驱动：`AgentService.toChatEvent()` 处理 `ModelCallEndEvent`，提取 `inputTokens/outputTokens`
- 异步写入：`Mono.fromRunnable().subscribeOn(boundedElastic).subscribe()`，不阻塞 SSE 流
- 回合级缓存：`providerName/modelName` 在对话开始时解析，避免重复查库+解密 API Key

**API 端点**:
```
GET    /api/token-usage/current-month          # 本月汇总
GET    /api/token-usage/month/{year}/{month}   # 指定月份
GET    /api/token-usage/recent-months?months=6 # 最近 N 个月趋势
GET    /api/token-usage/logs?limit=50          # 使用流水明细
GET    /api/token-usage/admin/tenant-users     # 租户用户排行 (admin + tenant_admin)
POST   /api/token-usage/test-record            # 测试接口(手动记录)
```

**前端页面**:
- `/token-usage` - Token 统计页 (本月汇总卡片 + 趋势图表 + 流水表格 + 管理员视图)

**后续优化方向**:
- 配额管理：Token 月度上限、超额告警
- 成本分析：按模型/用户/部门统计费用

### 5. 模型提供商配置 (成熟度: ⭐⭐⭐⭐⭐)

**实际实现**:
- ✅ 三级作用域: PLATFORM (平台级) / TENANT (租户级) / USER (用户级)
- ✅ 就近覆盖解析: 用户级 → 租户级 → 平台级,优先级递减
- ✅ 支持 7+ 提供商: DashScope / DeepSeek / OpenAI / Ollama / Anthropic Claude / Google Gemini / 火山方舟

**已支持的模型提供商**:
| 提供商 | 类型 | 默认模型 | API Key 环境变量 | 特点 |
|-------|------|---------|-----------------|------|
| **dashscope** | DashScope | qwen-plus | DASHSCOPE_API_KEY | 阿里云通义千问 |
| **deepseek** | OpenAI 兼容 | deepseek-chat | DEEPSEEK_API_KEY | 中文能力强 |
| **openai** | OpenAI | gpt-4.1-mini | OPENAI_API_KEY | GPT 系列 |
| **ollama** | Ollama | qwen2.5:7b | 无需 | 本地运行,完全免费 |
| **anthropic** | Anthropic | claude-sonnet-4-20250514 | ANTHROPIC_API_KEY | Claude 3.5/3.7/4 系列 |
| **gemini** | Gemini | gemini-2.0-flash | GEMINI_API_KEY | Google Gemini 2.0/2.5 |
| **volcengine** | OpenAI 兼容 | doubao-seed-2-1-pro | ARK_API_KEY | 火山方舟/豆包 |

**API 端点**:
```
GET    /api/config/model-providers         # 获取提供商列表
POST   /api/config/model-providers         # 新增配置
PUT    /api/config/model-providers/{id}    # 修改配置
DELETE /api/config/model-providers/{id}    # 删除配置
POST   /api/config/model-providers/{id}/test # 测试连接
```

**ModelFactory 构建逻辑**:
```java
public Model createModel(ModelProviderConfig cfg) {
    return switch (provider) {
        case "deepseek"   -> buildDeepSeek(cfg);     // OpenAI 兼容协议
        case "volcengine" -> buildVolcengine(cfg);   // 火山方舟/豆包
        case "openai"     -> buildOpenAi(cfg);       // OpenAI/Kimi/vLLM
        case "ollama"     -> buildOllama(cfg);       // 本地 Ollama
        case "anthropic"  -> buildAnthropic(cfg);    // Claude 3.5/3.7/4
        case "gemini"     -> buildGemini(cfg);       // Google Gemini
        default           -> buildDashScope(cfg);    // 阿里云通义千问
    };
}
```

**模型容错配置**:
- 最大重试 3 次，指数退避（1s → 2s → 4s）
- 超时 120s
- 所有提供商统一使用 `ExecutionConfig` 配置

### 6. MCP 服务器集成 (成熟度: ⭐⭐⭐⭐)

**实际实现**:
- ✅ 平台级共享: 所有租户共用同一套 MCP 服务器配置
- ✅ 支持两种传输: stdio (本地进程) / http (远程服务)
- ✅ 已配置 5 个免费 MCP: Git / GitHub / Chrome DevTools / SQLite / PostgreSQL

**已配置的 MCP 服务器**:
| 名称 | 传输 | 用途 | 状态 |
|------|------|------|------|
| Git MCP | stdio | Git 仓库操作 | ✅ 已配置 |
| GitHub MCP | http | GitHub API 集成 | ✅ 已配置 |
| Chrome DevTools | http | 浏览器自动化 | ✅ 已配置 |
| SQLite MCP | stdio | SQLite 数据库 | ✅ 已配置 |
| PostgreSQL MCP | stdio | PostgreSQL 数据库 | ✅ 已配置 |

**⚠️ 注意事项**:
- AgentScope Java **不支持直接连接远程 MCP**,需使用 `mcp-remote` 桥接
- stdio 类型的 MCP 需要在服务器上安装对应 npm 包

**API 端点**:
```
GET    /api/config/mcp-servers         # 获取 MCP 服务器列表
POST   /api/config/mcp-servers         # 新增配置
PUT    /api/config/mcp-servers/{id}    # 修改配置
DELETE /api/config/mcp-servers/{id}    # 删除配置
POST   /api/config/mcp-servers/{id}/test # 测试连接
```

### 7. AgentScope 满血能力 (成熟度: ⭐⭐⭐⭐⭐)

**实际实现**:
- ✅ **HarnessAgent 单例**: 全局一个实例,按 `(userId, sessionId)` 隔离状态
- ✅ **工作区人格**: `D:/claw-agent/.agentscope/workspace/AGENTS.md` 定义 Agent 人设
- ✅ **分层记忆**: 短期记忆(会话内) + 长期记忆(跨会话持久化)
- ✅ **上下文压缩**: 消息数超阈值自动蒸馏摘要 (`CompactionConfig`)
- ✅ **Redis 状态存储**: `RedisAgentStateStore` 支持分布式部署
- ✅ **AgentTraceMiddleware**: 执行链路追踪
- ✅ **PerformanceMiddleware**: 性能监控（对话/推理/执行/模型调用四维度耗时）
- ✅ **GuardrailsMiddleware**: 安全护栏（Prompt Injection 防护 + 输出脱敏）
- ✅ **思考过程透传**: `ThinkingBlockStart/Delta/EndEvent` 映射为 SSE 事件，前端可折叠展示
- ✅ **跨会话记忆**: `SessionSummaryService` 对话结束自动生成摘要，新会话注入系统提示词
- ✅ **技能自学习**: `SkillCuratorConfig` 已接入，7天间隔/30天stale/90天归档
- ✅ **子 Agent 委派**: `SubagentDeclaration` 编程式注册 + 提示词后缀双通道
- ✅ **Plan Mode**: 支持只读规划模式

**Middleware 链执行顺序**:
```
GuardrailsMiddleware (输入过滤 + 输出脱敏)
  → AgentTraceMiddleware (执行链路追踪)
    → PerformanceMiddleware (性能监控)
      → HarnessAgent 内置 Middleware
```

**配置位置**:
```yaml
claw:
  agent:
    state-store-type: redis  # redis / json
    workspace-root: D:/claw-agent/.agentscope/workspace
```

### 8. 渠道管理 (成熟度: ⭐⭐⭐⭐⭐)

**实际实现**:
- ✅ 渠道绑定: 支持微信/钉钉/飞书等外部渠道
- ✅ Webhook 接收: `POST /api/webhook/{channelType}` 接收外部消息
- ✅ 适配器模式: `ChannelAdapter` 接口支持扩展新渠道
- ✅ **消息投递**: Webhook 消息解析后投递到 Agent 会话，收集回复后通过渠道发回
- ✅ **签名验证**: `ChannelAdapter.verifySignature()` 默认方法，各渠道可覆盖实现具体算法
- ✅ **群成员同步**: `syncGroupMembers()` 调用适配器 `fetchGroupMembers()` 拉取最新成员

**消息路由流程**:
```
Webhook 接收 → 签名验证 → 路由到用户 → 构建 LoginUser → Agent 对话 → 收集回复 → 渠道发回
```

**单聊 vs 群聊**:
- 单聊：根据 `channelType + channelUserId` 查找绑定用户，投递到个人会话
- 群聊：根据 `channelType + groupId` 查找群成员，取第一个成员投递到共享会话

**API 端点**:
```
GET    /api/channels                    # 渠道绑定列表
POST   /api/channels                    # 新增渠道绑定
PUT    /api/channels/{id}               # 修改渠道绑定
DELETE /api/channels/{id}               # 删除渠道绑定
POST   /api/webhook/{channelType}       # Webhook 消息接收
```

**前端页面**:
- `/system/channels` - 渠道管理页

### 9. 邮件配置 (成熟度: ⭐⭐⭐⭐)

**实际实现**:
- ✅ SMTP 配置: 支持自定义邮件服务器配置
- ✅ 邮件发送: `EmailService` 支持发送 HTML/纯文本邮件
- ✅ 三级作用域: 平台/租户/用户级邮件配置

**API 端点**:
```
GET    /api/email-config                # 获取邮件配置
POST   /api/email-config                # 保存邮件配置
POST   /api/email-config/test           # 测试邮件发送
POST   /api/email/send                  # 发送邮件
```

**前端页面**:
- `/system/email-config` - 邮件配置页

### 10. 定时任务 (成熟度: ⭐⭐⭐⭐)

**实际实现**:
- ✅ 任务 CRUD: 创建/编辑/删除定时任务
- ✅ Cron 表达式: 支持标准 Cron 语法 + 常用模板
- ✅ 任务启停: 启用/禁用定时任务
- ✅ 手动执行: 支持手动触发任务执行
- ✅ 执行日志: 记录每次执行结果和错误信息
- ✅ 邮件通知: 任务完成后可选邮件通知

**API 端点**:
```
GET    /api/scheduled-tasks             # 任务列表
POST   /api/scheduled-tasks             # 新增任务
PUT    /api/scheduled-tasks/{id}        # 修改任务
DELETE /api/scheduled-tasks/{id}        # 删除任务
POST   /api/scheduled-tasks/{id}/toggle # 启用/禁用
POST   /api/scheduled-tasks/{id}/run    # 手动执行
GET    /api/scheduled-tasks/{id}/logs   # 执行日志
```

**前端页面**:
- `/scheduled-tasks` - 定时任务管理页

### 11. 预设模板市场 (成熟度: ⭐⭐⭐⭐)

**实际实现**:
- ✅ 模板浏览: 展示已发布的预设模板
- ✅ 模板使用: 一键复制到个人模板
- ✅ 搜索排序: 支持按名称搜索、按使用次数排序
- ✅ 图标展示: 多种预设图标和渐变色磁贴

**API 端点**:
```
GET    /api/marketplace/presets         # 市场模板列表
POST   /api/marketplace/presets/{id}/use # 使用模板
```

**前端页面**:
- `/marketplace` - 预设模板市场

### 12. 流水线编排 (成熟度: ⭐⭐⭐⭐)

**实际实现**:
- ✅ 流水线 CRUD: 创建/编辑/删除流水线
- ✅ 步骤定义: Markdown 格式定义执行步骤
- ✅ 预设绑定: 流水线可绑定预设人格
- ✅ 外贸客户开发: 内置 7 步自动化流水线模板
- ✅ 结果表格优化: 支持表格横向滚动、CSV 导出、全屏预览

**API 端点**:
```
GET    /api/pipelines                   # 流水线列表
POST   /api/pipelines                   # 新增流水线
PUT    /api/pipelines/{code}            # 修改流水线
DELETE /api/pipelines/{code}            # 删除流水线
```

**前端页面**:
- `/pipelines` - 流水线管理页

### 13. 权限模式动态化 (成熟度: ⭐⭐⭐⭐⭐)

**实际实现**:
- ✅ 五种权限模式: DEFAULT / ACCEPT_EDITS / EXPLORE / BYPASS / DONT_ASK
- ✅ write_file 动态放行: ACCEPT_EDITS/BYPASS 自动放行，DEFAULT/EXPLORE 需 HITL
- ✅ delete_note 动态放行: 与 write_file 保持一致行为
- ✅ 危险操作强制 HITL: dangerous_delete / drop_table / force_push 始终需确认

**配置方式**:
```
Agent 配置 → 运行参数 → permission_mode
```

---

## ⚠️ 未实现或简化的能力

### 1. 部门管理 (成熟度: ⭐⭐)

**现状**:
- ✅ 数据库表 `sys_dept` 已创建
- ✅ `DeptController` 存在
- ❌ **前端页面无**: `/system/dept` 路由不存在
- ❌ **业务逻辑简化**: 用户表中虽有 `dept_id` 字段,但未实际使用

**建议**:
- 如果不需要部门层级,可移除该功能
- 如需保留,需开发前端页面和完善 Service 层逻辑

### 2. 数据字典 (成熟度: ⭐⭐⭐)

**现状**:
- ✅ 数据库表 `sys_dict_type` / `sys_dict_data` 已创建
- ✅ `DictController` 存在
- ⚠️ **前端页面缺失**: `/system/dict` 路由可能不存在或未完善
- ️ **使用场景有限**: 主要用于枚举值翻译,非核心功能

**建议**:
- 保持现状,作为辅助功能
- 后续可根据实际需求完善前端页面

### 3. 在线监控 (成熟度: ⭐⭐)

**现状**:
- ✅ `MonitorController` 存在
- ⚠️ **功能简化**: 仅记录在线用户到内存 Map (`OnlineUserTracker`)
-  **无实时推送**: 无法实时监控用户活动
- ❌ **前端页面缺失**: `/system/online` 路由可能不存在

**建议**:
- 标记为"实验性功能"
- 如需完整监控,需引入 WebSocket 或 SSE 推送

### 4. 流水线剧本 (成熟度: ⭐⭐⭐)

**现状**:
- ✅ 数据库表 `agent_pipeline` 已创建
- ✅ `PipelineController` 存在
- ⚠️ **使用率低**: 大部分用户直接使用预设人格,较少配置流水线
- ️ **文档不足**: 缺少流水线配置示例和使用指南

**建议**:
- 保持现状,作为高级功能
- 补充文档和示例

### 5. Skills 技能库 (成熟度: ⭐⭐⭐)

**现状**:
- ✅ 文件系统已创建: `skills/code-review/`, `skills/data-analysis/`, `skills/regex-tester/`
- ✅ SKILL.md 文件已编写
- ⚠️ **未实际测试**: 未验证 Agent 能否正确调用这些技能
- ️ **依赖检查缺失**: 未自动检测 ESLint/Pylint/pandas 等依赖是否安装

**建议**:
- 标记为"实验性功能"
- 补充依赖安装指南
- 实际测试技能调用流程

---

##  功能成熟度总览

| 功能模块 | 成熟度 | 说明 |
|---------|-------|------|
| **多租户与权限** | ⭐⭐⭐⭐⭐ | 完整实现，生产就绪 |
| **智能对话系统** | ⭐⭐⭐⭐⭐ | 完整实现，SSE 流式 + HITL + 思考过程展示 |
| **动态工具注册** | ⭐⭐⭐⭐⭐ | 完整实现，注解扫描 + 运行时管理 + 浏览器工具 |
| **模型提供商配置** | ⭐⭐⭐⭐⭐ | 完整实现，三级作用域 + 8+ 提供商 |
| **权限模式动态化** | ⭐⭐⭐⭐⭐ | 完整实现，五种模式动态调整 |
| **AgentScope 能力** | ⭐⭐⭐⭐⭐ | 满血版，Middleware 链 + 思考透传 + 跨会话记忆 |
| **渠道管理** | ⭐⭐⭐⭐⭐ | 完整实现，Webhook 投递 + 签名验证 + 群成员同步 |
| **Token 使用统计** | ⭐⭐⭐⭐⭐ | 自动记录 + 数据库 + API + 前端完整 |
| **定时任务** | ⭐⭐⭐⭐ | 完整实现，CRUD + 启停 + 日志 + 邮件通知 |
| **邮件配置** | ⭐⭐⭐⭐ | 完整实现，SMTP 配置 + 三级作用域 |
| **预设模板市场** | ⭐⭐⭐⭐ | 完整实现，浏览 + 使用 + 搜索 |
| **流水线编排** | ⭐⭐⭐⭐ | 完整实现，内置外贸客户开发模板 |
| **MCP 服务器集成** | ⭐⭐⭐⭐ | 基本实现，需注意 mcp-remote 桥接 |
| **部门管理** | ⭐⭐ | 数据库存在，前端缺失 |
| **数据字典** | ⭐⭐⭐ | 基本实现，使用场景有限 |
| **在线监控** | ⭐⭐ | 简化实现，无实时推送 |
| **Skills 技能库** | ⭐⭐⭐ | 文件已创建，未实际测试 |

---

## 🎯 推荐的使用方式

### 个人开发者

1. **单机部署**: Docker Compose 一键启动 MySQL + Redis + Backend + Frontend
2. **免费模型**: 配置 Ollama (本地) 或 Groq (云端免费额度)
3. **核心功能**: 聊天对话 + 工具调用 + 笔记管理
4. **可选功能**: Token 统计(手动记录)、MCP 服务器(Git/SQLite)

### 小团队 (5-50人)

1. **多租户隔离**: 为每个部门/项目创建独立租户
2. **权限控制**: 平台管理员管理租户,租户管理员管理本团队用户
3. **模型配置**: 平台级配置免费模型,租户级可覆盖为付费模型
4. **成本管控**: Token 统计监控各成员使用情况

### 技术爱好者

1. **扩展工具**: 编写 `@ToolSet` 注解类,自动注册新工具
2. **接入 MCP**: 配置 Chrome DevTools / PostgreSQL 等外部服务
3. **测试技能**: 尝试 `code-review` / `data-analysis` 等 Skills
4. **贡献代码**: Fork 仓库,提交 PR 增加新功能

---

## 🔧 技术栈总结

### 后端
- **框架**: Spring Boot 3.5 (WebFlux 响应式)
- **Agent**: AgentScope Java 2.0 (HarnessAgent 单例)
- **ORM**: MyBatis Plus 3.5.9
- **数据库**: MySQL 8.0 + Flyway 10 (迁移管理)
- **缓存**: Redis 7.0 (会话状态存储)
- **认证**: Spring Security Reactive + JJWT 0.12

### 前端
- **框架**: Next.js 16 (App Router) + React 19 + TypeScript 5
- **UI**: Tailwind CSS 4 + Base UI/shadcn
- **状态**: Zustand (auth store, chat store)
- **图表**: Recharts (Token 统计页面)
- **HTTP**: fetch + ReadableStream (SSE 解析)

### 部署
- **容器化**: Docker + Docker Compose (可选)
- **反向代理**: Nginx / Caddy (生产环境推荐)
- **监控**: Actuator + 自定义日志 (traceId 全链路追踪)

---

## 📝 下一步优化建议

### P1 (高优先级)
1. **可观测性集成**: 引入 Micrometer + Prometheus，暴露 Middleware 指标到监控平台
2. **CodeExecutionTool**: 实现代码执行工具，支持 Python/Java 代码沙箱运行
3. **Gemini 模型支持**: 接入 Google Gemini 模型提供商

### P2 (中优先级)
4. **定时任务增强**: 失败重试机制、执行统计可视化
5. **技能测试**: 实际验证 `code-review` / `data-analysis` 技能调用流程
6. **子 Agent 验证**: 测试复杂任务拆解为子任务的效果
7. **浏览器通知**: 前端实现 Web Push 通知
8. **暗色模式**: 前端支持暗色主题切换

### P3 (低优先级)
9. **流水线模板扩展**: 周报生成、竞品监控、行业资讯聚合等场景
10. **WebSocket 推送**: 实现在线监控实时推送
11. **配额管理**: Token 月度上限、超额告警
12. **成本分析**: 按模型/用户/部门统计费用
13. **部门管理前端**: 开发部门管理页面

---

**文档维护者**: Claw Agent Team  
**反馈渠道**: GitHub Issues  
**许可证**: MIT
