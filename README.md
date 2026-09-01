# claw-agent

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
│       ├── db/migration/ # Flyway 数据库迁移脚本（V1-V35）
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

### 1. 准备数据库

```sql
CREATE DATABASE claw_agent DEFAULT CHARSET utf8mb4;
```

表结构由 Flyway 启动时自动迁移（`backend/src/main/resources/db/migration/`，只增不改）。

### 2. 启动后端（端口 8080）

```bash
cd backend
# 方式 1：直接运行（开发环境）
mvn spring-boot:run

# 方式 2：打包后运行（生产环境）
mvn clean package -DskipTests
java -jar target/backend-1.0.0-SNAPSHOT.jar
```

首次启动时 Flyway 会自动执行数据库迁移（V1-V35），创建所有表结构和初始数据。

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
| `MYSQL_USER` / `MYSQL_PASSWORD` | `root` / `root` | MySQL 账号 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DB` | `localhost` / `6379` / `0` | Redis 连接 |
| `REDIS_PASSWORD` | （空） | Redis 密码 |
| `CLAW_JWT_SECRET` | 占位值 | **生产必改**：JWT 签名密钥（同时派生配置加密密钥） |
| `CORS_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` | 允许的前端来源，逗号分隔 |
| `DASHSCOPE_API_KEY` / `DEEPSEEK_API_KEY` / `OPENAI_API_KEY` | （空） | 模型 API Key 兜底（库中未配置时读取） |
| `HTTP_PROXY_HOST` / `HTTP_PROXY_PORT` | （空） | HTTP 代理（中国大陆网络环境下 web_search 工具需要） |

**注意**：PowerShell 设置环境变量示例：
```powershell
$env:CLAW_JWT_SECRET="your-secret-key-change-in-production"
$env:MYSQL_HOST="192.168.1.100"
```

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

#### 3. 动态工具注册系统 (⭐⭐⭐⭐⭐)
- **注解扫描自动注册**：`@ToolSet` 定义工具集元数据，启动时自动扫描
- **运行时启用/禁用**：`/api/tools/{code}/enable` / `/disable`
- **工具详情提取**：反射扫描 `@Tool` 方法，生成结构化描述
- **6 大分类体系**：utility / search / data / code / ai / system
- **内置工具集**：system_tools / math_tools / multi_search / note_tools

#### 4. Token 使用统计 (⭐⭐⭐⭐)

**状态**: ⚠️ 部分实现

- **流水记录表**：`token_usage_log` 记录每次模型调用（prompt_tokens/completion_tokens/total_tokens）
- **月度汇总表**：`token_usage_summary` 按月聚合统计，数据库触发器自动维护
- **管理员视图**：租户内用户 Token 使用排行（仅平台管理员/租户管理员可见）
- ⚠️ **已知限制**：自动拦截逻辑未实现，当前通过测试接口 `POST /api/token-usage/test-record` 手动验证链路

#### 5. 模型提供商配置 (⭐⭐⭐⭐⭐)
- **三级作用域**：PLATFORM (平台级) / TENANT (租户级) / USER (用户级)
- **就近覆盖解析**：用户级 → 租户级 → 平台级，优先级递减
- **8+ 提供商支持**：DashScope / DeepSeek / OpenAI / Ollama / Groq / HuggingFace / 阿里云百炼
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
- **子 Agent 委派** (⚠️ 部分实现)：框架支持，未充分验证复杂任务拆解效果
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
**最后更新**: 2026-08-28
