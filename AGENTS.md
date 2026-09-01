# AGENTS.md — claw-agent 项目编码规约

> 本文件是本项目所有代码贡献者（包括 AI 编码助手）必须遵循的规则。
- 技术栈：Spring Boot 3.5（Java 17 编译，兼容 21 运行）+ AgentScope Java 2.0（满血版）+ MyBatis Plus + MySQL + Flyway + Redis + WebFlux SSE；前端：Next.js 16 + React 19 + TypeScript + Tailwind CSS 4 + Base UI/shadcn + Zustand。

## 1. 项目概述

claw-agent 是一个个人 Agent 平台：

- 工程结构：仓库根目录下 `backend/`（Spring Boot 后端）与 `frontend/`（Next.js 前端）并列；
  后端构建与启动的工作目录必须是 `backend/`；AgentScope 工作区与上传目录使用绝对路径（`D:/claw-agent/.agentscope/workspace`、`D:/claw-agent/data/uploads`）；
- 单实例 `HarnessAgent` 服务所有用户，按 `(userId, sessionId)` 隔离状态；
- 满血接入 AgentScope：工作区、分层记忆、上下文压缩、技能自学习、子 Agent、Plan Mode、权限系统（HITL）、Middleware、事件流式；
- 用户体系：Spring Security + JWT 无状态认证，RBAC（admin / tenant_admin / common 三角色）；
- 聊天支持文字 / 图片 / 文件上传（多模态消息）。

## 2. 分层架构（阿里规约分层）

```
controller  → 只做参数校验与协议转换，不写业务逻辑；按模块分包：auth（认证）/ chat（对话与上传）/ agent（预设与配置）/ system（用户/角色/部门/菜单/租户/字典/日志）
service     → 业务逻辑层（接口 + Impl：接口 extends IService<T>，实现 extends ServiceImpl<Mapper, Entity> implements 接口，实现类放 service/impl）
mapper      → 数据访问层（MyBatis Plus BaseMapper；自定义 SQL 一律写在 resources/mapper/*.xml，禁用 @Select 等注解 SQL）
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

## 8. 安全规约

- 密码一律 BCrypt 加密存储，禁止明文与可逆加密；
- JWT secret 生产环境必须替换：`application.yml` 中仅为开发默认值，生产通过环境变量 `CLAW_JWT_SECRET` 覆盖（`claw.jwt.secret: ${CLAW_JWT_SECRET:默认值}`），不得依赖默认值上线；
- 文件上传必须校验：扩展名白名单 + 大小上限 + 存储路径防目录穿越（`..` 拦截）；
- 危险路径（`.ssh/`、`.env`、`.git/` 等）的写操作必须触发 HITL 人工确认。

## 9. 数据库规约

- 表名、字段名小写下划线（`sys_user`、`create_time`）；
- 业务表必备六审计字段：`create_time`、`update_time`、`creator`、`updater`、`creator_id`、`updater_id`；实体继承 `model.BaseEntity`，六字段由 `AuditMetaObjectHandler` 自动填充（操作人取自 `UserContextHolder`，ID 来自 JWT 的 `userId` 声明），业务代码禁止手工 set；
- 日志分表存储：业务操作日志 `sys_oper_log`（由 ReactiveSupport 统一写入，成功失败均记）、登录登出日志 `sys_login_log`（由 AuthService 写入），新管理端接口必须接入操作日志；
- 删除优先逻辑删除；索引命名 `uk_` / `idx_` 前缀；
- **表结构变更一律通过 Flyway 迁移脚本**：`backend/src/main/resources/db/migration/V<n>__<描述>.sql`；
- 迁移脚本只增不改（已执行的脚本修改会导致校验失败）；新脚本版本号递增；
- 脚本内部保持幂等（`CREATE TABLE IF NOT EXISTS` / `INSERT ... WHERE NOT EXISTS`）。

## 10. 前端规约（frontend/ 独立工程）

- 前端为 `frontend/` 独立 Next.js 16（App Router）工程，与后端前后端分离；后端不承载任何静态资源与页面（历史 `static/` 目录已删除）；
- UI 用 shadcn（Base UI 底层）+ Tailwind CSS 4，状态管理用 Zustand；不引入其他重型框架；
- 所有 API 调用统一封装在 `frontend/src/lib/api.ts`，集中处理 token、错误码、401 跳转与 SSE 流解析；
- SSE 用 `fetch` + `ReadableStream` 解析（需携带 JWT），不用原生 `EventSource`（无法加请求头）；
- 开发期 `/api` 经 `next.config.ts` rewrites 代理到后端 `:8080`；跨域放行配置走后端 `claw.cors.allowed-origin-patterns`。

## 11. Git 规约

- 提交信息格式：`<type>: <描述>`，type ∈ {feat, fix, docs, refactor, test, chore}；
- 每个提交聚焦一件事，禁止超大杂糅提交。
