# claw-agent

<p align="center">
  <a href="#chinese">中文</a> · <a href="#english">English</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License" />
  <img src="https://img.shields.io/badge/Java-17+-orange.svg" alt="Java Version" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-green.svg" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Next.js-16-black.svg" alt="Next.js" />
  <img src="https://img.shields.io/badge/AgentScope-2.0-purple.svg" alt="AgentScope" />
  <img src="https://img.shields.io/github/stars/KittyMi/claw-agent?style=social" alt="GitHub Stars" />
</p>

---

<a id="chinese"></a>

# claw-agent (中文)

**个人/小团队私有化部署的 AI Agent 平台** - 基于 AgentScope Java 2.0的多租户智能助手服务。

单实例服务所有用户，按 `(userId, sessionId)` 隔离会话状态；支持流式对话（SSE）、多模态消息、HITL 人工审批、动态工具系统、Token 使用统计与完整的 RBAC 权限体系。

**核心特点**：
- 🆓 **零成本启动**：内置 Ollama/Groq/HuggingFace 等免费模型提供商，无需 API Key 即可使用
- 🔧 **动态工具注册**：`@ToolSet` 注解扫描自动注册，运行时启用/禁用，零侵入扩展
- 📊 **Token 统计追踪**：自动记录模型调用消耗，月度汇总，管理员视图
- 🔐 **企业级权限**：平台管理员 / 租户管理员 / 普通用户三级 RBAC，菜单级授权
- 🌐 **MCP 协议支持**：集成 Git/GitHub/Chrome DevTools 等外部工具服务器

## 技术栈

| 层 | 技术 |
|---|---|
| **后端** | Java 17+ · Spring Boot 3.5（WebFlux 响应式 + SSE）· Spring Security（JWT 无状态） |
| **Agent** | AgentScope Java 2.0：工作区人格、分层记忆、上下文压缩、子 Agent、Plan Mode、Middleware |
| **数据** | MySQL 8.x + Flyway 迁移 · MyBatis Plus 3.5 · Redis 7.x（Agent 会话状态存储） |
| **前端** | Next.js 16 · React 19 · TypeScript 5 · Tailwind CSS 4 · Base UI/shadcn · Zustand · Recharts |

> **架构说明**：单实例架构，一个 Spring Boot 服务服务所有用户，通过 `(userId, sessionId)` 实现会话隔离。适合个人开发者和小团队（5-50人），不适合大规模并发场景。

## 项目结构

```
claw-agent/
├── backend/              # Spring Boot 后端（Maven）
│   ├── src/main/java/    # Java 源码（controller/service/mapper/config/security/tool）
│   └── src/main/resources/
│       ├── db/migration/ # Flyway 数据库迁移脚本（V1-V49）
│       └── mapper/       # MyBatis XML SQL
├── frontend/             # Next.js 前端（npm）
│   ├── src/app/          # App Router 页面（login/chat/system/*）
│   ├── src/components/   # React 组件（AppShell/ChatView/UI Primitives）
│   └── src/lib/          # API Client / Types / Utils
├── docs/                 # 完整文档（平台定义/能力清单/API 规范）
└── AGENTS.md             # 编码规约（所有贡献者必读）
```

后端分层：`controller → service → mapper`（依赖方向单向，DTO 隔离），包结构 `com.claw.agent.<layer>`。详细架构见 [docs/PLATFORM_DEFINITION.md](docs/PLATFORM_DEFINITION.md)。

## 环境要求

- **JDK**: 17+（推荐 21，Spring Boot 3.5 最低要求 17）
- **Maven**: 3.8+
- **Node.js**: 18+（前端开发，推荐 20）
- **MySQL**: 8.0+
- **Redis**: 6.0+（可选，单机开发可降级为 JSON 文件存储）
- **Docker**（可选）: 用于容器化部署

## 快速开始

### 0. 配置环境变量（首次启动必做）

```bash
# Windows PowerShell
cp .env.example .env
# 编辑 .env 文件，填写实际的数据库密码、Redis 密码、JWT Secret 等
notepad .env
```

**重要**：`.env` 文件包含敏感信息，已被 `.gitignore` 忽略，不会提交到 Git。

### 1. 准备数据库

```sql
CREATE DATABASE claw_agent DEFAULT CHARSET utf8mb4;
```

表结构由 Flyway 启动时自动迁移（`backend/src/main/resources/db/migration/`，只增不改）。

### 2. 启动后端（端口 8080）

**推荐方式：使用启动脚本（自动加载 .env）**

```bash
# Windows PowerShell
./start-backend.ps1
```

**传统方式：手动设置环境变量**

```bash
cd backend

# Windows PowerShell - 手动加载 .env
Get-Content ..\.env | Where-Object { $_ -match '^\w+=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

# 方式 1：直接运行（开发环境）
mvn spring-boot:run

# 方式 2：打包后运行（生产环境）
mvn clean package -DskipTests
java -jar target/backend-1.0.0-SNAPSHOT.jar
```

首次启动时 Flyway 会自动执行数据库迁移（V1-V49），创建所有表结构和初始数据。

### 3. 启动前端（端口 3000）

```bash
cd frontend
npm install      # 首次需要安装依赖
npm run dev      # 开发模式（热重载）
# npm run build  # 生产构建
# npm run start  # 生产模式
```

访问 <http://localhost:3000>，默认账号：`admin` / `admin123`（平台管理员）。

**配置说明**：模型提供商与 Agent 运行参数在登录后的「平台治理 → 模型配置」页面维护（数据库三级作用域：USER > TENANT > PLATFORM），无需修改代码重启服务。首次启动时会自动创建 admin 账号并分配平台管理员角色。

## 环境变量

所有配置均有本地开发默认值，生产部署按需覆盖：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` | `localhost` / `3306` / `claw_agent` | MySQL 连接 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | （必填） | MySQL 账号密码 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DB` | `localhost` / `6379` / `0` | Redis 连接 |
| `REDIS_PASSWORD` | （空） | Redis 密码 |
| `CLAW_JWT_SECRET` | （必填） | **生产必改**：JWT 签名密钥（同时派生配置加密密钥） |
| `CORS_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` | 允许的前端来源，逗号分隔 |
| `HTTP_PROXY_HOST` / `HTTP_PROXY_PORT` | （空） | HTTP 代理（中国大陆网络环境下 web_search 工具需要） |

**注意**：PowerShell 设置环境变量示例：
```powershell
$env:CLAW_JWT_SECRET="your-secret-key-change-in-production"
$env:MYSQL_HOST="192.168.1.100"
```

完整模板见 [.env.example](.env.example)。

## 主要功能

### ✅ 已实现的核心能力

#### 1. 多租户与权限体系 (⭐⭐⭐⭐⭐)
- **三级角色**：平台管理员 (`admin`) / 租户管理员 (`tenant_admin`) / 普通用户 (`common`)
- **RBAC 菜单授权**：后端按角色聚合菜单树，前端动态渲染导航栏
- **租户数据隔离**：所有查询强制 `.eq(tenantId, current.getTenantId())`
- **平台管理员专属**：创建/删除租户、分配租户管理员

#### 2. 智能对话系统 (⭐⭐⭐⭐⭐)
- **SSE 流式输出**：`POST /api/chat/stream` 返回 Server-Sent Events
- **多模态支持**：Base64 图片附件直接投喂模型
- **预设人格叠加**：`presetCode` 参数注入模板提示词
- **HITL 人工确认**：敏感工具执行前弹窗审批 (`/api/chat/confirm`)
- **会话历史持久化**：自动保存聊天记录到数据库
- **会话归档**：归档/恢复会话便于组织管理

#### 3. 动态工具注册系统 (⭐⭐⭐⭐⭐)
- **注解扫描自动注册**：`@ToolSet` 定义工具集元数据，启动时自动扫描
- **运行时启用/禁用**：`/api/tools/{code}/enable` / `/disable`
- **工具详情提取**：反射扫描 `@Tool` 方法，生成结构化描述
- **6 大分类体系**：utility / search / data / code / ai / system
- **内置工具集**：system_tools / math_tools / multi_search / note_tools

#### 4. Token 使用统计 (⭐⭐⭐⭐)
- **流水记录表**：`token_usage_log` 记录每次模型调用（prompt_tokens/completion_tokens/total_tokens）
- **月度汇总表**：`token_usage_summary` 按月聚合统计，数据库触发器自动维护
- **管理员视图**：租户内用户 Token 使用排行（仅平台管理员/租户管理员可见）
- ⚠️ **已知限制**：自动拦截逻辑未实现，当前通过测试接口 `POST /api/token-usage/test-record` 手动验证链路

#### 5. 模型提供商配置 (⭐⭐⭐⭐⭐)
- **三级作用域**：PLATFORM (平台级) / TENANT (租户级) / USER (用户级)
- **就近覆盖解析**：用户级 → 租户级 → 平台级，优先级递减
- **8+ 提供商支持**：DashScope / DeepSeek / OpenAI / Ollama / Groq / HuggingFace / 阿里云百炼 / 火山引擎
- **免费模型内置**：Ollama (本地) / Groq (云端免费额度) / HuggingFace

#### 6. MCP 服务器集成 (⭐⭐⭐⭐)
- **平台级共享**：所有租户共用同一套 MCP 服务器配置
- **两种传输协议**：stdio (本地进程) / http (远程服务)
- **5 个免费 MCP**：Git / GitHub / Chrome DevTools / SQLite / PostgreSQL
- ⚠️ **注意事项**：AgentScope Java 不支持直接连接远程 MCP，需使用 `mcp-remote` 桥接

#### 7. AgentScope 满血能力 (⭐⭐⭐⭐)
- **HarnessAgent 单例**：全局一个实例，按 `(userId, sessionId)` 隔离状态
- **工作区人格**：`.agentscope/workspace/AGENTS.md` 定义 Agent 人设
- **分层记忆**：短期记忆(会话内) + 长期记忆(跨会话持久化)
- **上下文压缩**：消息数超阈值自动蒸馏摘要
- **Redis 状态存储**：`RedisAgentStateStore` 支持分布式部署

### ⚠️ 部分实现/待完善

- **在线监控** (🚧 开发中)：简化实现（内存 Map 记录），无 WebSocket 实时推送
- **Skills 技能库** (❌ 规划中)：目录未创建，需初始化 `skills/` 目录并编写 SKILL.md
- **子 Agent 委派** (️ 部分实现)：框架支持，未充分验证复杂任务拆解效果
- **Token 自动拦截** (⚠️ 部分实现)：测试接口可用，自动提取 usage 需查阅 AgentScope 2.0 官方文档

## Docker 部署（可选）

提供 `docker-compose.yml` 一键启动完整环境：

```bash
# 1. 编辑 docker-compose.yml 中的环境变量（特别是 CLAW_JWT_SECRET）
# 2. 启动所有服务
docker-compose up -d

# 3. 查看日志
docker-compose logs -f backend

# 4. 停止服务
docker-compose down
```

服务包含：MySQL 8.4 + Redis 7 + Backend (8080) + Frontend (3000)

## 开发约定

- **编码规约**：见 [AGENTS.md](AGENTS.md) - 分层、命名、注释、异常日志、Flyway、安全基线
- **提交信息格式**：`<type>: <描述>`，type ∈ {feat, fix, docs, refactor, test, chore}
- **表结构变更**：一律走 Flyway 迁移脚本（版本号递增，脚本幂等，只增不改）
- **API 规范**：非流式接口统一 `Result<T>`，SSE 接口返回 `Flux<ServerSentEvent<String>>`
- **时间格式**：`yyyy-MM-dd HH:mm:ss` (UTC+8)，Jackson 全局配置

## 相关文档

- 📘 [平台定义文档](docs/PLATFORM_DEFINITION.md) - 完整的功能清单、API 规范、数据库设计
- 📊 [能力成熟度评估](docs/PLATFORM_CAPABILITIES.md) - 基于最终实现的功能分级与定位
- 🔧 [动态工具注册系统](docs/DYNAMIC_TOOL_REGISTRY.md) - @ToolSet 注解使用指南
- 📈 [Token 使用统计系统](docs/TOKEN_USAGE_TRACKING.md) - 数据库设计与 API 规范
- 🆓 [免费模型与 MCP 配置](docs/FREE_MCP_AND_SKILLS_PLATFORM.md) - 零成本启动指南
- 🌐 [HTTP 代理配置](docs/PROXY_CONFIG.md) - 中国大陆网络环境下 web_search 工具配置
- 📝 [流水线剧本使用指南](docs/PIPELINE_USAGE_GUIDE.md) - 3 个内置流水线详解与自定义教程

## 常见问题 (FAQ)

### Q1: 如何添加新的工具集？

只需三步：
1. 创建工具类并添加 `@ToolSet` 注解
2. 在类中编写 `@Tool` 注解的方法
3. 重启应用，自动扫描注册

示例：
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
        return "晴天,25°C";
    }
}
```

### Q2: 如何实现自动 Token 记录？

当前版本已完成数据库设计和测试接口，但自动拦截逻辑尚未实现。后续方案：
1. **方案 A**：研究 AgentScope 2.0 正确的 Middleware API
2. **方案 B**：在 `AgentService.doChat()` 的 `end` 事件中提取 usage
3. **方案 C**：创建 ModelWrapper 包装器，在调用前后记录

详见 [TOKEN_USAGE_INTEGRATION.md](docs/TOKEN_USAGE_INTEGRATION.md)

### Q3: 如何配置免费模型？

访问 **平台治理 → 模型提供商**，点击"新增"：
- **Ollama**: `provider=ollama`, `baseUrl=http://localhost:11434/v1`, `apiKey=null`
- **Groq**: `provider=openai`, `baseUrl=https://api.groq.com/openai/v1`, `apiKey=<your-key>`

详细配置见 [FREE_MODEL_PROVIDERS.md](docs/FREE_MODEL_PROVIDERS.md)

### Q4: 租户管理员能看到其他租户的数据吗？

**不能**。所有 Service 层查询都强制加上 `.eq(tenantId, current.getTenantId())`，确保数据隔离。平台管理员(`admin`)除外，可跨租户查询和管理。

**角色权限矩阵**：
- **平台管理员(admin)**：全局最高权限，管理所有租户、分配租户管理员
- **租户管理员(tenant_admin)**：仅限本租户内管理用户/角色/菜单
- **普通用户(common)**：仅限本租户业务功能（对话、查看自己的 Token 统计）

### Q5: 如何备份与恢复数据？

```bash
# 备份
mysqldump -u root -p claw_agent > backup_$(date +%Y%m%d).sql

# 恢复
mysql -u root -p claw_agent < backup_20260827.sql
```

## 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

## 贡献

欢迎提交 Issue 和 Pull Request！请遵循 [AGENTS.md](AGENTS.md) 中的编码规约。

---

**作者**: Ryder  
**平台维护者**: Claw Agent Team  
**反馈渠道**: GitHub Issues  
**最后更新**: 2026-09-01

[↑ 回到顶部](#claw-agent)

---

<a id="english"></a>

# claw-agent (English)

**A private AI Agent platform for individuals and small teams** - Multi-tenant intelligent assistant service based on AgentScope Java 2.0.

Single-instance service serving all users, with session isolation by `(userId, sessionId)`; supports streaming chat (SSE), multimodal messages, HITL human approval, dynamic tool system, Token usage tracking, and complete RBAC permission system.

**Core Features**:
- 🆓 **Zero-cost startup**: Built-in free model providers (Ollama/Groq/HuggingFace), no API Key required
-  **Dynamic tool registration**: `@ToolSet` annotation scanning for automatic registration, runtime enable/disable, zero-intrusion extension
- 📊 **Token usage tracking**: Automatic model call consumption recording, monthly summary, admin view
- 🔐 **Enterprise-grade permissions**: Three-tier RBAC (Platform Admin / Tenant Admin / Common User), menu-level authorization
-  **MCP protocol support**: Integration with external tool servers (Git/GitHub/Chrome DevTools)

## Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 17+ · Spring Boot 3.5 (WebFlux reactive + SSE) · Spring Security (JWT stateless) |
| **Agent** | AgentScope Java 2.0: Workspace personality, layered memory, context compression, sub-agents, Plan Mode, Middleware |
| **Data** | MySQL 8.x + Flyway migrations · MyBatis Plus 3.5 · Redis 7.x (Agent session state storage) |
| **Frontend** | Next.js 16 · React 19 · TypeScript 5 · Tailwind CSS 4 · Base UI/shadcn · Zustand · Recharts |

> **Architecture**: Single-instance architecture, one Spring Boot service serving all users, session isolation via `(userId, sessionId)`. Suitable for individual developers and small teams (5-50 people), not for large-scale concurrent scenarios.

## Project Structure

```
claw-agent/
├── backend/              # Spring Boot backend (Maven)
│   ├── src/main/java/    # Java source code (controller/service/mapper/config/security/tool)
│   └── src/main/resources/
│       ├── db/migration/ # Flyway database migration scripts (V1-V49)
│       └── mapper/       # MyBatis XML SQL
├── frontend/             # Next.js frontend (npm)
│   ├── src/app/          # App Router pages (login/chat/system/*)
│   ├── src/components/   # React components (AppShell/ChatView/UI Primitives)
│   └── src/lib/          # API Client / Types / Utils
├── docs/                 # Complete documentation (platform definition/capabilities/API specs)
└── AGENTS.md             # Coding conventions (must-read for all contributors)
```

Backend layering: `controller → service → mapper` (unidirectional dependency, DTO isolation), package structure `com.claw.agent.<layer>`. Detailed architecture see [docs/PLATFORM_DEFINITION.md](docs/PLATFORM_DEFINITION.md).

## Requirements

- **JDK**: 17+ (recommended 21, Spring Boot 3.5 minimum requirement 17)
- **Maven**: 3.8+
- **Node.js**: 18+ (frontend development, recommended 20)
- **MySQL**: 8.0+
- **Redis**: 6.0+ (optional, single-machine development can downgrade to JSON file storage)
- **Docker** (optional): For containerized deployment

## Quick Start

### 1. Prepare Database

```sql
CREATE DATABASE claw_agent DEFAULT CHARSET utf8mb4;
```

Table structure is automatically migrated by Flyway at startup (`backend/src/main/resources/db/migration/`, append-only).

### 2. Start Backend (Port 8080)

```bash
cd backend
# Method 1: Direct run (development environment)
mvn spring-boot:run

# Method 2: Package then run (production environment)
mvn clean package -DskipTests
java -jar target/backend-1.0.0-SNAPSHOT.jar
```

On first startup, Flyway will automatically execute database migrations (V1-V49), creating all table structures and initial data.

### 3. Start Frontend (Port 3000)

```bash
cd frontend
npm install      # Install dependencies (first time only)
npm run dev      # Development mode (hot reload)
# npm run build  # Production build
# npm run start  # Production mode
```

Visit <http://localhost:3000>, default account: `admin` / `admin123` (Platform Administrator).

**Configuration**: Model providers and Agent runtime parameters are maintained in the "Platform Governance → Model Configuration" page after login (database three-tier scope: USER > TENANT > PLATFORM), no need to modify code or restart services. On first startup, the admin account will be automatically created and assigned the platform administrator role.

## Environment Variables

All configurations have local development defaults, override as needed for production deployment:

| Variable | Default | Description |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` | `localhost` / `3306` / `claw_agent` | MySQL connection |
| `MYSQL_USER` / `MYSQL_PASSWORD` | (required) | MySQL credentials |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DB` | `localhost` / `6379` / `0` | Redis connection |
| `REDIS_PASSWORD` | (empty) | Redis password |
| `CLAW_JWT_SECRET` | (required) | **Production must change**: JWT signing key (also derives config encryption key) |
| `CORS_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` | Allowed frontend origins, comma-separated |
| `HTTP_PROXY_HOST` / `HTTP_PROXY_PORT` | (empty) | HTTP proxy (web_search tool needs this in mainland China network environment) |

**Note**: PowerShell set environment variable example:
```powershell
$env:CLAW_JWT_SECRET="your-secret-key-change-in-production"
$env:MYSQL_HOST="192.168.1.100"
```

See [.env.example](.env.example) for complete template.

## Main Features

### ✅ Implemented Core Capabilities

#### 1. Multi-tenancy & Permission System (⭐⭐⭐⭐⭐)
- **Three-tier roles**: Platform Admin (`admin`) / Tenant Admin (`tenant_admin`) / Common User (`common`)
- **RBAC menu authorization**: Backend aggregates menu tree by role, frontend dynamically renders navigation
- **Tenant data isolation**: All queries enforce `.eq(tenantId, current.getTenantId())`
- **Platform Admin exclusive**: Create/delete tenants, assign tenant administrators

#### 2. Intelligent Chat System (⭐⭐⭐⭐⭐)
- **SSE streaming output**: `POST /api/chat/stream` returns Server-Sent Events
- **Multimodal support**: Base64 image attachments directly fed to models
- **Preset personality overlay**: `presetCode` parameter injects template prompts
- **HITL human confirmation**: Popup approval before sensitive tool execution (`/api/chat/confirm`)
- **Session history persistence**: Automatically save chat records to database
- **Session archive**: Archive/unarchive sessions for organization

#### 3. Dynamic Tool Registration System (⭐⭐⭐⭐⭐)
- **Annotation scanning auto-registration**: `@ToolSet` defines toolset metadata, auto-scanned at startup
- **Runtime enable/disable**: `/api/tools/{code}/enable` / `/disable`
- **Tool detail extraction**: Reflective scan of `@Tool` methods, generate structured descriptions
- **6-category taxonomy**: utility / search / data / code / ai / system
- **Built-in toolsets**: system_tools / math_tools / multi_search / note_tools

#### 4. Token Usage Statistics (⭐⭐⭐⭐)
- **Usage log table**: `token_usage_log` records each model call (prompt_tokens/completion_tokens/total_tokens)
- **Monthly summary table**: `token_usage_summary` monthly aggregation, database trigger auto-maintains
- **Admin view**: User Token usage ranking within tenant (visible only to platform/tenant admins)
- ⚠️ **Known limitation**: Auto-interception logic not implemented, currently use test interface `POST /api/token-usage/test-record` to manually verify pipeline

#### 5. Model Provider Configuration (⭐⭐⭐⭐⭐)
- **Three-tier scope**: PLATFORM / TENANT / USER
- **Nearest override resolution**: User-level → Tenant-level → Platform-level, priority descending
- **8+ provider support**: DashScope / DeepSeek / OpenAI / Ollama / Groq / HuggingFace / Alibaba Cloud Bailian / Volcengine
- **Free models built-in**: Ollama (local) / Groq (cloud free quota) / HuggingFace

#### 6. MCP Server Integration (⭐⭐⭐⭐)
- **Platform-level sharing**: All tenants share the same MCP server configuration
- **Two transport protocols**: stdio (local process) / http (remote service)
- **5 free MCPs**: Git / GitHub / Chrome DevTools / SQLite / PostgreSQL
- ⚠️ **Note**: AgentScope Java does not support direct remote MCP connection, requires `mcp-remote` bridge

#### 7. AgentScope Full Capabilities (⭐⭐⭐⭐)
- **HarnessAgent singleton**: Global single instance, state isolated by `(userId, sessionId)`
- **Workspace personality**: `.agentscope/workspace/AGENTS.md` defines Agent persona
- **Layered memory**: Short-term (in-session) + Long-term (cross-session persistence)
- **Context compression**: Auto-distill summary when message count exceeds threshold
- **Redis state storage**: `RedisAgentStateStore` supports distributed deployment

### ⚠️ Partially Implemented/Pending

- **Online monitoring** (🚧 In development): Simplified implementation (memory Map recording), no WebSocket real-time push
- **Skills library** (❌ Planned): Directory not created, need to initialize `skills/` directory and write SKILL.md
- **Sub-agent delegation** (⚠️ Partially implemented): Framework supported, complex task decomposition effects not fully verified
- **Token auto-interception** (⚠️ Partially implemented): Test interface usable, auto-extract usage needs to consult AgentScope 2.0 official docs

## Docker Deployment (Optional)

Provides `docker-compose.yml` for one-click full environment startup:

```bash
# 1. Edit environment variables in docker-compose.yml (especially CLAW_JWT_SECRET)
# 2. Start all services
docker-compose up -d

# 3. View logs
docker-compose logs -f backend

# 4. Stop services
docker-compose down
```

Services include: MySQL 8.4 + Redis 7 + Backend (8080) + Frontend (3000)

## Development Conventions

- **Coding conventions**: See [AGENTS.md](AGENTS.md) - layering, naming, comments, exception logging, Flyway, security baseline
- **Commit message format**: `<type>: <description>`, type ∈ {feat, fix, docs, refactor, test, chore}
- **Schema changes**: Always go through Flyway migration scripts (version increment, idempotent scripts, append-only)
- **API spec**: Non-streaming interfaces unified `Result<T>`, SSE interfaces return `Flux<ServerSentEvent<String>>`
- **Time format**: `yyyy-MM-dd HH:mm:ss` (UTC+8), Jackson global config

## Related Documentation

- 📘 [Platform Definition](docs/PLATFORM_DEFINITION.md) - Complete feature list, API specs, database design
- 📊 [Capability Maturity Assessment](docs/PLATFORM_CAPABILITIES.md) - Feature grading and positioning based on final implementation
- 🔧 [Dynamic Tool Registration System](docs/DYNAMIC_TOOL_REGISTRY.md) - @ToolSet annotation usage guide
- 📈 [Token Usage Tracking System](docs/TOKEN_USAGE_TRACKING.md) - Database design and API specs
-  [Free Models & MCP Configuration](docs/FREE_MCP_AND_SKILLS_PLATFORM.md) - Zero-cost startup guide
- 🌐 [HTTP Proxy Configuration](docs/PROXY_CONFIG.md) - web_search tool configuration in mainland China network environment
- 📝 [Pipeline Playbook Usage Guide](docs/PIPELINE_USAGE_GUIDE.md) - 3 built-in pipelines detailed explanation and customization tutorial

## FAQ

### Q1: How to add a new toolset?

Just three steps:
1. Create tool class and add `@ToolSet` annotation
2. Write `@Tool` annotated methods in the class
3. Restart application, auto-scan and register

Example:
```java
@ToolSet(
    code = "weather_tools",
    name = "Weather Tools",
    description = "Query weather forecast",
    category = "utility",
    enabledByDefault = true
)
@Component
public class WeatherTools {
    @Tool(name = "get_weather", description = "Get city weather")
    public String getWeather(@ToolParam("City name") String city) {
        return "Sunny, 25°C";
    }
}
```

### Q2: How to implement automatic Token recording?

Current version has completed database design and test interface, but auto-interception logic not yet implemented. Future solutions:
1. **Option A**: Research correct AgentScope 2.0 Middleware API
2. **Option B**: Extract usage from `end` event in `AgentService.doChat()`
3. **Option C**: Create ModelWrapper wrapper, record before/after calls

See [TOKEN_USAGE_INTEGRATION.md](docs/TOKEN_USAGE_INTEGRATION.md)

### Q3: How to configure free models?

Visit **Platform Governance → Model Providers**, click "Add":
- **Ollama**: `provider=ollama`, `baseUrl=http://localhost:11434/v1`, `apiKey=null`
- **Groq**: `provider=openai`, `baseUrl=https://api.groq.com/openai/v1`, `apiKey=<your-key>`

Detailed configuration see [FREE_MODEL_PROVIDERS.md](docs/FREE_MODEL_PROVIDERS.md)

### Q4: Can tenant admins see other tenants' data?

**No**. All Service layer queries enforce `.eq(tenantId, current.getTenantId())` to ensure data isolation. Platform Admin (`admin`) is exception, can cross-tenant query and manage.

**Role permission matrix**:
- **Platform Admin(admin)**: Global highest privilege, manage all tenants, assign tenant admins
- **Tenant Admin(tenant_admin)**: Manage users/roles/menus within own tenant only
- **Common User(common)**: Own tenant business functions only (chat, view own Token stats)

### Q5: How to backup and restore data?

```bash
# Backup
mysqldump -u root -p claw_agent > backup_$(date +%Y%m%d).sql

# Restore
mysql -u root -p claw_agent < backup_20260827.sql
```

## License

MIT License - See [LICENSE](LICENSE) file

## Contributing

Welcome to submit Issues and Pull Requests! Please follow coding conventions in [AGENTS.md](AGENTS.md).

---

**Author**: Ryder  
**Platform Maintainers**: Claw Agent Team  
**Feedback Channel**: GitHub Issues  
**Last Updated**: 2026-09-01

[↑ Back to Top](#claw-agent)
