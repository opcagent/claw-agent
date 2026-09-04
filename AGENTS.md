# AGENTS.md — claw-agent 项目编码规约

> 本文件是本项目所有代码贡献者（包括 AI 编码助手）必须遵循的规则。
- 技术栈：Spring Boot 3.5（Java 17 编译，兼容 21 运行）+ AgentScope Java 2.0（满血版）+ MyBatis Plus + MySQL + Flyway + Redis + WebFlux SSE；前端：Next.js 16 + React 19 + TypeScript + Tailwind CSS 4 + Base UI/shadcn + Zustand。

## 1. 项目概述

claw-agent 是一个面向中小企业和个人开发者的 Agent 平台：

- 工程结构：仓库根目录下 `backend/`（Spring Boot 后端）与 `frontend/`（Next.js 前端）并列；
  后端构建与启动的工作目录必须是 `backend/`；AgentScope 工作区与上传目录使用绝对路径（`D:/claw-agent/.agentscope/workspace`、`D:/claw-agent/data/uploads`）；
- 渐进式弹性架构：单机模式 Redis 不可用时自动降级为内存/本地文件，零依赖启动；多实例模式 Redis 可用时 HITL/在线追踪/配置广播跨节点共享，支持水平扩展；
- 全局单例 `HarnessAgent` 服务所有用户，按 `(userId, sessionId)` 隔离状态；
- 满血接入 AgentScope：工作区、分层记忆、上下文压缩、技能自学习、子 Agent、Plan Mode、权限系统（HITL）、Middleware、事件流式；
- 用户体系：Spring Security + JWT 无状态认证，RBAC（admin / tenant_admin / common 三角色）；
- 聊天支持文字 / 图片 / 文件上传（多模态消息）。

## 2. 分层架构（阿里规约分层）

```
controller  → 只做参数校验与协议转换，不写业务逻辑；按模块分包：auth（认证）/ chat（对话与上传）/ agent（预设与配置）/ system（用户/角色/部门/菜单/租户/字典/日志）
service     → 业务逻辑层（接口 + Impl：接口 extends IService<T>，实现 extends ServiceImpl<Mapper, Entity> implements 接口，实现类放 service/impl）
mapper      → 数据访问层（MyBatis Plus BaseMapper；优先用内置方法，自定义 SQL 仅限多表 JOIN 等场景且写在 resources/mapper/*.xml，禁用 @Select 等注解 SQL）；
              涉及联表（如 sys_user JOIN sys_user_tenant）的查询禁止在 Java 层用 inSql 拼接字符串，必须在 XML 中定义
model/pojo  → 实体（对应表，业务实体一律 extends BaseEntity）、DTO（请求/响应对象）、枚举
config      → Spring 配置类（Agent / Security / MyBatis / CORS）
security    → JWT 过滤器、UserDetailsService
agent/tools → AgentScope 自定义工具（@Tool 注解方法）
common      → Result / ResultCode / BizException / 全局异常处理器
```

- **依赖方向**：controller → service → mapper，禁止反向依赖；
- **DTO 隔离**：实体类不直接暴露给前端，出入参一律用 DTO。

## 3. 命名规约

- 类名 UpperCamelCase；方法、变量、参数 lowerCamelCase；常量全大写下划线分隔；
- 跨类复用的常量（角色键、权限前缀、状态码等）统一收敛到 `common` 包静态常量类（如 `RoleConstants`），禁止各类里重复定义私有魔法值；
- 抽象类以 `Abstract`/`Base` 开头；异常类以 `Exception` 结尾；枚举以语义命名（如 `Role`）；
- POJO 布尔属性不加 `is` 前缀（避免序列化框架歧义）；
- 包名全小写：`com.claw.agent.<layer>`。

## 4. 注释规约（强制）

- 所有类必须有 Javadoc：说明职责、关键设计；
- 所有 public 方法必须有 Javadoc：参数、返回值、异常含义；
- 关键业务逻辑、易踩坑处写行内中文注释说明「为什么」而非「是什么」；
- 禁止无意义注释（如 `// 获取用户` 对应 `getUser()`）。

## 5. 异常与日志

- 业务失败抛 `BizException(ResultCode.X)`，由 `GlobalExceptionHandler` 统一转 `Result`；
- 禁止吞异常（空 catch）；catch 后必须记日志或转换抛出；
- 日志用 Slf4j 占位符风格：`log.info("user login, username={}", username)`，禁止字符串拼接；
- 异常日志必须带堆栈：`log.error("msg", e)`。

## 6. 接口规约

- REST 路径：`/api/<模块>/<动作>`，例如 `/api/auth/login`、`/api/chat/stream`；
- **REST 路径命名（强制 camelCase）**：路径段必须使用驼峰命名（camelCase），禁止使用连字符（kebab-case）。新增接口必须遵守，历史遗留接口逐步迁移。示例：✅ `/api/auth/selectTenant`、`/api/config/paramKeys`；❌ `/api/auth/select-tenant`、`/api/config/param-keys`；
- **响应式返回类型（强制）**：项目基于 WebFlux，所有控制器方法必须返回响应式类型 `Mono<Result<T>>` 或 `Flux<Result<T>>`，禁止直接返回阻塞类型 `Result<T>`（Spring Security 响应式拦截器会断言失败抛 `IllegalStateException`）；SSE 接口返回 `Flux<ServerSentEvent<String>>`；
- 需要登录的阻塞式接口一律用 `common.ReactiveSupport.call/run`（查询用 `call`，增删改用带模块/操作类型/描述的重载 `call/run` 并自动记操作日志），禁止手写 `SecurityUtil.currentUser().flatMap(... Mono.fromCallable ... subscribeOn ...)` 样板；禁止使用 `@AuthenticationPrincipal` 注解获取用户（应从 `ReactiveSupport.call` 的 lambda 参数获取）；放行接口（登录/注册）才允许裸 `Mono.fromCallable(...).subscribeOn(boundedElastic)`；
- 不需要登录的接口（如工具集查询）用 `Mono.fromCallable(() -> Result.ok(...)).subscribeOn(Schedulers.boundedElastic())` 包装，确保不阻塞事件循环；
- 所有需要登录的接口从 JWT 解析用户身份，禁止前端传 userId 决定操作对象（防越权）；
- ADMIN 专属接口加角色校验。

## 7. AgentScope 使用规约

- `HarnessAgent` 为全局单例 Bean，禁止 per-user 创建实例；
- 每次调用必须显式传 `RuntimeContext(userId, sessionId)`，这是多租户隔离的根基；
- Agent 会话状态默认存 Redis（`RedisAgentStateStore`），支持多实例部署与跨节点会话恢复；单机开发可将数据库三级配置项 `state_store_type` 改为 `json`（已数据库化，非 yml 配置）；
- 工作区（`.agentscope/workspace/`）是 Agent 人格与能力的 source of truth：
  - 人格改 `AGENTS.md`；知识放 `knowledge/`；技能放 `skills/`；子 Agent 放 `subagents/`；
- 中间件与工具中访问状态一律用 `RuntimeContext.resolveAgentState(ctx, agent)`，禁用 `agent.getAgentState()`（并发不安全）；
- 新增自定义工具用 `@Tool` 注解 + 注册进 Toolkit，并在权限系统中登记 ALLOW/ASK 规则。

## 7.1 工具集（ToolSet）开发规范

### 7.1.1 工具集元数据（@ToolSet 注解）

每个工具类必须添加 `@ToolSet` 注解，声明工具集元数据：

```java
@Slf4j
@ToolSet(
    code = "my_tools",           // 唯一标识：小写字母+下划线
    name = "我的工具集",          // 中文显示名称
    description = "提供XX功能",   // 50字以内功能描述
    category = "utility",        // 分类：utility/search/data/code/ai/system
    enabledByDefault = true,     // 新用户是否默认启用
    version = "1.0.0"            // 语义化版本号
)
public class MyTools { ... }
```

**必填字段**：
- `code` — 唯一标识，Java 常量定义在 `common.ToolCodes`，命名 snake_case
- `name` — 中文显示名称
- `description` — 功能描述（禁止 Markdown 或特殊字符）
- `category` — 必须在预定义分类中：`utility`/`search`/`data`/`code`/`ai`/`system`
- `enabledByDefault` — 默认启用状态

**可选字段**：
- `version` — 语义化版本号（默认 "1.0.0"）
- `dependencies` — 依赖的其他工具集 code 列表
- `requiresHITL` — 是否需要 HITL 审批（默认 false）
- `allowedRoles` — 适用角色列表（空表示所有角色）

### 7.1.2 工具方法（@Tool 注解）

工具方法使用 `@Tool` 注解，参数使用 `@ToolParam`：

```java
@Tool(name = "search_web", description = "联网搜索网页信息，返回标题和摘要")
public String searchWeb(
        @ToolParam(name = "query", description = "搜索关键词") String query,
        @ToolParam(name = "num_results", description = "返回结果数量", required = false) Integer numResults) {
    // 实现逻辑
}
```

**命名规范**：
- 工具名 `name` — snake_case（如 `search_web`、`get_current_time`）
- 参数名 `name` — snake_case（如 `num_results`、`date_format`）

**参数规范**：
- `required` — 是否必填（默认 true），可选参数需设默认值
- `description` — 参数说明，清晰描述用途和格式要求

### 7.1.3 工具注册流程

**自动注册**（无特殊构造参数）：
- 添加 `@ToolSet` 注解后，`ToolRegistry` 启动时自动扫描并注册
- 工具类必须有公共无参构造函数

**手动注册**（需要特殊构造参数）：
- 在 `AgentRegistry.buildHarnessAgent()` 中手动 `new` 实例
- 加入 `manualToolCodes` 集合跳过自动注册
- 示例：`NoteTools(workspace)`、`KnowledgeSearchTools(workspace)`、`MultiSearchTools(config, service, proxy)`

### 7.1.4 工具集开发检查清单

新增工具集时必须完成以下步骤：

- [ ] 在 `common/ToolCodes.java` 中定义 code 常量
- [ ] 工具类添加 `@ToolSet` 注解（含所有必填字段）
- [ ] 每个 `@Tool` 方法有完整 Javadoc（参数、返回值、异常说明）
- [ ] 需要特殊构造的工具加入 `manualToolCodes` 并在 `AgentRegistry` 手动注册
- [ ] 在 `CapabilityService` 中登记工具权限规则（ALLOW/ASK）
- [ ] 更新 `docs/TOOLS_REFERENCE.md` 文档

## 7.2 MCP 服务器集成规范

### 7.2.1 MCP 配置方式

MCP 服务器通过数据库三级作用域配置（`agent_mcp_server` 表），支持运行时动态增删：

```sql
INSERT INTO agent_mcp_server (code, name, transport, command, args, scope, owner_id) 
VALUES ('git', 'Git MCP', 'stdio', 'npx', '["-y", "@modelcontextprotocol/server-git"]', 'PLATFORM', NULL);
```

**传输协议**：
- `stdio` — 本地进程，通过标准输入输出通信（需安装对应 npm 包）
- `http` — 远程服务，通过 HTTP SSE 通信（需使用 `mcp-remote` 桥接）

### 7.2.2 MCP 开发注意事项

- AgentScope Java **不支持直接连接远程 MCP**，必须使用 `mcp-remote` 桥接
- stdio 类型需在服务器上安装对应 npm 包（如 `@modelcontextprotocol/server-git`）
- MCP 工具自动注册到 Toolkit，无需额外代码
- 可通过 `tools.json` 白名单控制允许的 MCP 工具

### 7.2.3 新增 MCP 服务器检查清单

- [ ] 确认传输协议类型（stdio/http）
- [ ] stdio 类型确认 npm 包已安装
- [ ] http 类型配置 `mcp-remote` 桥接地址
- [ ] 在 `agent_mcp_server` 表中添加配置记录
- [ ] 测试连接（`POST /api/config/mcp-servers/{id}/test`）
- [ ] 更新 `docs/PLATFORM_DEFINITION.md` 文档

## 7.3 技能（Skill）开发规范

### 7.3.1 技能文件结构

技能以 Markdown 文件形式存储在工作区 `skills/` 目录下：

```
workspace/
└── skills/
    ├── code-review/
    │   └── SKILL.md        # 技能定义文件
    ├── data-analysis/
    │   └── SKILL.md
    └── regex-tester/
        └── SKILL.md
```

### 7.3.2 SKILL.md 文件格式

```markdown
---
name: code-review
description: 代码审查，检查规范性和潜在问题
version: 1.0.0
author: system
triggers:
  - 代码审查
  - code review
dependencies:
  - eslint
  - pylint
---

# 代码审查技能

## 触发条件
当用户请求代码审查时触发此技能。

## 执行步骤
1. 读取目标代码文件
2. 运行 ESLint/Pylint 检查
3. 分析结果并生成报告
4. 提供修复建议

## 输出格式
- 问题列表（严重程度、位置、描述）
- 修复建议
- 总体评分
```

**Frontmatter 字段**：
- `name` — 技能唯一标识（snake_case）
- `description` — 技能功能描述
- `version` — 语义化版本号
- `author` — 作者（system/user）
- `triggers` — 触发关键词列表
- `dependencies` — 外部依赖（如 eslint、pandas）

### 7.3.3 技能自学习机制

AgentScope 内置技能自学习闭环：

1. **技能起草**：Agent 完成复杂任务后可调用 `propose_skill` 提议新技能
2. **后台整理**：`SkillCuratorConfig` 定期整理技能库
   - 间隔：7 天
   - Stale 阈值：30 天
   - 归档阈值：90 天
3. **技能加载**：新会话自动加载相关技能到上下文

### 7.3.4 新增技能检查清单

- [ ] 在 `skills/` 目录创建技能文件夹
- [ ] 编写 `SKILL.md` 文件（含完整 frontmatter）
- [ ] 明确触发条件和执行步骤
- [ ] 列出外部依赖（如有）
- [ ] 测试技能调用流程
- [ ] 更新 `docs/PLATFORM_DEFINITION.md` 文档

## 7.4 Middleware 开发规范

### 7.4.1 Middleware 类型

AgentScope 提供四种 Middleware 钩子：

| 钩子 | 触发时机 | 典型用途 |
|------|---------|----------|
| `onSystemPrompt` | 系统提示词构建 | 注入上下文、修改提示词 |
| `onAgent` | Agent 调用前后 | 输入过滤、输出脱敏 |
| `onReasoning` | 推理阶段 | 思考过程记录 |
| `onActing` | 执行阶段 | 工具调用拦截 |
| `onModelCall` | 模型调用前后 | Token 统计、性能监控 |

### 7.4.2 Middleware 注册顺序

Middleware 按注册顺序形成洋葱模型，先注册的先执行：

```java
// AgentRegistry 中的注册顺序
agentBuilder.middleware(guardrailsMiddleware)   // 1. 安全护栏（最先执行）
           .middleware(agentTraceMiddleware)     // 2. 执行链路追踪
           .middleware(performanceMiddleware);   // 3. 性能监控
```

### 7.4.3 Middleware 开发注意事项

- 输入过滤类 Middleware 注册为第一个（如 `GuardrailsMiddleware`）
- 性能监控类 Middleware 放在追踪之后
- Middleware 中禁止阻塞操作，耗时操作用 `Mono.fromCallable().subscribeOn(boundedElastic)`
- 异常处理：Middleware 异常不应阻断主流程，catch 后记日志并继续

## 7.5 子 Agent 开发规范

### 7.5.1 子 Agent 定义方式

**方式一：Markdown 文件（推荐）**

在 `subagents/` 目录创建 `.md` 文件：

```markdown
---
name: researcher
description: 研究助手，擅长信息检索和整理
model: deepseek:deepseek-chat
tools:
  - multi_search
  - browser
---

# 研究助手

你是一名专业的研究助手，擅长信息检索、整理和分析。

## 工作方式
1. 理解研究目标
2. 制定检索策略
3. 收集和整理信息
4. 生成结构化报告
```

**方式二：编程式注册（SubagentDeclaration）**

```java
SubagentDeclaration.builder()
    .name("researcher")
    .description("研究助手")
    .model(chatModel)
    .tools(Toolkit.builder().addTool(searchTools).build())
    .workspaceMode(WorkspaceMode.ISOLATED)
    .build();
```

### 7.5.2 子 Agent 调用方式

Agent 通过内置工具调用子 Agent：
- `agent_spawn(name)` — 启动子 Agent
- `agent_send(name, message)` — 向子 Agent 发送消息

### 7.5.3 子 Agent 开发注意事项

- 子 Agent 工作区默认隔离（`ISOLATED`），互不干扰
- 子 Agent 继承父 Agent 的权限模式
- 后台任务完成后通过 system-reminder 通知父 Agent
- 避免过度嵌套（子 Agent 再调用子 Agent），建议最多 2 层

## 8. 安全规约

- 密码一律 BCrypt 加密存储，禁止明文与可逆加密；
- JWT secret 生产环境必须替换：`application.yml` 中仅为开发默认值，生产通过环境变量 `CLAW_JWT_SECRET` 覆盖（`claw.jwt.secret: ${CLAW_JWT_SECRET:默认值}`），不得依赖默认值上线；
- 文件上传必须校验：扩展名白名单 + 大小上限 + 存储路径防目录穿越（`..` 拦截）；
- 危险路径（`.ssh/`、`.env`、`.git/` 等）的写操作必须触发 HITL 人工确认。

## 9. 数据库规约

- 表名、字段名小写下划线（`sys_user`、`create_time`）；
- 业务表必备六审计字段：`create_time`、`update_time`、`creator`、`updater`、`creator_id`、`updater_id`；实体继承 `model.BaseEntity`，六字段由 `AuditMetaObjectHandler` 自动填充（操作人取自 `UserContextHolder`，ID 来自 JWT 的 `userId` 声明），业务代码禁止手工 set；
- **用户表结构**：`sys_user` 仅存跨组织共享的基础属性（用户名/密码/昵称/手机/邮箱/性别/状态/备注），**不含** `tenant_id` / `dept_id`；用户的组织归属（租户、部门、角色、职位）统一存 `sys_user_tenant`，是组织维度的唯一 source of truth；按组织/部门查询用户必须经 `sys_user_tenant` 联表，禁止在 `sys_user` 上直接过滤；
- 日志分表存储：业务操作日志 `sys_oper_log`（由 ReactiveSupport 统一写入，成功失败均记）、登录登出日志 `sys_login_log`（由 AuthService 写入），新管理端接口必须接入操作日志；
- 删除优先逻辑删除；索引命名 `uk_` / `idx_` 前缀；
- **表结构变更一律通过 Flyway 迁移脚本**：`backend/src/main/resources/db/migration/V<n>__<描述>.sql`；
- 迁移脚本只增不改（已执行的脚本修改会导致校验失败）；新脚本版本号递增；
- 脚本内部保持幂等（`CREATE TABLE IF NOT EXISTS` / `INSERT ... WHERE NOT EXISTS`）。

### 9.1 MyBatis Plus 使用规约（强制）

- **能用 BaseMapper 内置方法解决的，禁止写自定义 XML SQL**：单表 CRUD、条件查询、分页、批量操作一律用 `BaseMapper` / `IService` 内置方法 + `LambdaQueryWrapper`；
- **允许写自定义 XML 的场景**（必须说明原因）：
  - 多表 JOIN 查询（如用户-角色-菜单联表）；
  - MySQL 特有语法（如 `ON DUPLICATE KEY UPDATE`）；
  - 复杂动态 SQL（多条件组合、子查询）；
- **禁止事项**：
  - 禁止在 XML 中拼接多条 SQL（`DELETE; INSERT;`），MySQL JDBC 默认不支持多语句执行；
  - 禁止在 XML 中写原始 `INSERT` 绕过 `AuditMetaObjectHandler`（审计字段会为空），批量插入用 `IService.saveBatch()`；
  - 禁止在 Mapper 接口使用 `@Select`、`@Insert`、`@Update`、`@Delete` 注解写 SQL（统一放 XML 或用 BaseMapper）；
- **Mapper 接口规范**：
  - 继承 `BaseMapper<Entity>`，无自定义方法时接口体留空即可；
  - 自定义方法仅放多表 JOIN 等 BaseMapper 无法覆盖的场景；
  - 自定义 SQL 写在 `resources/mapper/*.xml`，文件头注释说明为什么不用 BaseMapper；
- **Service 层规范**：
  - 继承 `IService<Entity>` + `ServiceImpl<Mapper, Entity>`；
  - 优先用 `IService` 内置方法（`save`、`saveBatch`、`update`、`remove`、`getOne`、`list`、`page`）；
  - 复杂查询用 `LambdaQueryWrapper` 构建条件，禁止手写 SQL 字符串；
  - 批量插入必须用 `saveBatch()`（触发 `AuditMetaObjectHandler` 填充审计字段），禁止循环单条 `insert` 或 XML 批量 INSERT。

## 10. 前端规约（frontend/ 独立工程）

- 前端为 `frontend/` 独立 Next.js 16（App Router）工程，与后端前后端分离；后端不承载任何静态资源与页面（历史 `static/` 目录已删除）；
- UI 用 shadcn（Base UI 底层）+ Tailwind CSS 4，状态管理用 Zustand；不引入其他重型框架；
- 所有 API 调用统一封装在 `frontend/src/lib/api.ts`，集中处理 token、错误码、401 跳转与 SSE 流解析；
- SSE 用 `fetch` + `ReadableStream` 解析（需携带 JWT），不用原生 `EventSource`（无法加请求头）；
- 开发期 `/api` 经 `next.config.ts` rewrites 代理到后端 `:8080`；跨域放行配置走后端 `claw.cors.allowed-origin-patterns`。

## 11. Git 规约

- 提交信息格式：`<type>: <描述>`，type ∈ {feat, fix, docs, refactor, test, chore}；
- 每个提交聚焦一件事，禁止超大杂糅提交。
