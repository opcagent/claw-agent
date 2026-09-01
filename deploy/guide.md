# claw-agent 部署指南

> 本文档描述 claw-agent 平台的生产环境部署目录结构、构建流程与运维规范。

## 1. 部署目录结构

### 1.1 推荐部署目录布局

```
/opt/claw-agent/                    # 部署根目录（Linux 示例）
├── backend/                        # 后端服务
│   ├── backend-1.0.0-SNAPSHOT.jar  # Spring Boot 可执行 JAR
│   ├── application-prod.yml        # 生产环境配置（可选，覆盖默认配置）
│   ├── data/                       # 运行时数据目录
│   │   └── uploads/                # 用户上传文件存储
│   │       └── {username}/         # 按用户隔离的附件目录
│   ├── .agentscope/                # AgentScope 工作区
│   │   └── workspace/              # Agent 人格与能力 source of truth
│   │       ├── AGENTS.md           # Agent 人格定义
│   │       ├── knowledge/          # 知识库
│   │       ├── skills/             # 技能目录
│   │       └── subagents/          # 子 Agent 定义
│   └── logs/                       # 应用日志（可选，默认输出到 stdout）
├── frontend/                       # 前端服务
│   ├── .next/                      # Next.js 构建产物
│   ├── node_modules/               # 运行时依赖
│   ├── package.json                # 依赖声明
│   └── next.config.ts              # Next.js 配置
└── nginx/                          # Nginx 配置（可选）
    └── claw-agent.conf             # 反向代理配置
```

### 1.2 源码仓库结构

```
claw-agent/                         # 项目根目录
├── backend/                        # Spring Boot 后端（Maven）
│   ├── src/main/java/              # Java 源码
│   │   └── com/claw/agent/
│   │       ├── controller/         # 控制器层（auth/chat/agent/system）
│   │       ├── service/            # 业务逻辑层
│   │       ├── mapper/             # 数据访问层
│   │       ├── model/              # 实体/DTO/枚举
│   │       ├── config/             # 配置类
│   │       ├── security/           # JWT 过滤器、UserDetailsService
│   │       ├── tool/               # AgentScope 自定义工具
│   │       └── common/             # 通用组件（Result/BizException）
│   ├── src/main/resources/
│   │   ├── db/migration/           # Flyway 数据库迁移脚本
│   │   ├── mapper/                 # MyBatis XML SQL
│   │   └── application.yml         # 主配置文件
│   └── pom.xml                     # Maven 依赖管理
├── frontend/                       # Next.js 前端（npm）
│   ├── src/
│   │   ├── app/                    # App Router 页面
│   │   ├── components/             # React 组件
│   │   ├── lib/                    # API Client / Types / Utils
│   │   └── store/                  # Zustand 状态管理
│   ├── package.json                # npm 依赖声明
│   └── next.config.ts              # Next.js 配置（含 API 代理）
├── deploy/                         # 部署资源（脚本、文档、Nginx 配置）
└── docs/                           # 项目开发文档
```

## 2. 环境要求

### 2.1 基础依赖

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| **JDK** | 17+（推荐 21） | Spring Boot 3.5 运行时 |
| **Maven** | 3.8+ | 后端构建工具 |
| **Node.js** | 18+（推荐 20） | 前端构建与运行 |
| **MySQL** | 8.0+ | 主数据库 |
| **Redis** | 6.0+ | Agent 会话状态存储（可选，可降级为 JSON 文件） |

### 2.2 系统资源建议

| 规模 | CPU | 内存 | 磁盘 | 说明 |
|------|-----|------|------|------|
| 个人/开发 | 2 核 | 4 GB | 20 GB | 单用户或少量并发 |
| 小团队（5-20人） | 4 核 | 8 GB | 50 GB | 日常使用 |
| 中型团队（20-50人） | 8 核 | 16 GB | 100 GB | 较高并发 |

## 3. 构建流程

### 3.1 后端构建

```bash
# 进入后端目录
cd backend

# 方式 1：开发环境直接运行
mvn spring-boot:run

# 方式 2：生产环境打包
mvn clean package -DskipTests

# 产物位置
ls target/backend-1.0.0-SNAPSHOT.jar
```

**构建产物说明**：
- `target/backend-1.0.0-SNAPSHOT.jar`：可执行 Fat JAR，包含所有依赖
- 首次启动时 Flyway 自动执行数据库迁移（`V1__xxx.sql` ~ `V36__xxx.sql`）

### 3.2 前端构建

```bash
# 进入前端目录
cd frontend

# 安装依赖（首次或依赖变更时）
npm install

# 开发模式（热重载）
npm run dev

# 生产构建
npm run build

# 生产模式运行
npm run start
```

**构建产物说明**：
- `.next/`：Next.js 构建产物目录
- 生产模式使用 `node .next/standalone/server.js` 或 `npm run start`（端口 3000）

## 4. 配置说明

### 4.1 环境变量（生产必配）

| 变量 | 默认值 | 必填 | 说明 |
|------|--------|------|------|
| `CLAW_JWT_SECRET` | 开发占位值 | ✅ | JWT 签名密钥，生产必须替换 |
| `MYSQL_HOST` | localhost | ✅ | MySQL 主机地址 |
| `MYSQL_PORT` | 3306 | ⚪ | MySQL 端口 |
| `MYSQL_DB` | claw_agent | ⚪ | 数据库名 |
| `MYSQL_USER` | root | ✅ | 数据库用户名 |
| `MYSQL_PASSWORD` | root | ✅ | 数据库密码 |
| `REDIS_HOST` | localhost | ⚪ | Redis 主机（未安装可留空） |
| `REDIS_PORT` | 6379 | ⚪ | Redis 端口 |
| `REDIS_PASSWORD` | （空） | ⚪ | Redis 密码 |
| `CORS_ORIGINS` | http://localhost:3000 | ⚪ | 允许的前端来源，逗号分隔 |
| `HTTP_PROXY_HOST` | （空） | ⚪ | HTTP 代理（中国大陆网络需要） |
| `HTTP_PROXY_PORT` | 0 | ⚪ | 代理端口 |

### 4.2 数据库初始化

```sql
-- 创建数据库（字符集必须 utf8mb4）
CREATE DATABASE claw_agent DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建专用用户（生产建议）
CREATE USER 'claw'@'%' IDENTIFIED BY 'your-strong-password';
GRANT ALL PRIVILEGES ON claw_agent.* TO 'claw'@'%';
FLUSH PRIVILEGES;
```

表结构由 Flyway 在应用首次启动时自动迁移创建，无需手动建表。

### 4.3 生产配置文件（可选）

可在 JAR 同目录放置 `application-prod.yml` 覆盖默认配置：

```yaml
# /opt/claw-agent/backend/application-prod.yml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?useUnicode=true&characterEncoding=UTF-8&useSSL=true&serverTimezone=Asia/Shanghai
  data:
    redis:
      host: ${REDIS_HOST}
      password: ${REDIS_PASSWORD}

claw:
  jwt:
    secret: ${CLAW_JWT_SECRET}
  cors:
    allowed-origin-patterns: ${CORS_ORIGINS:https://your-domain.com}
  upload:
    dir: ./data/uploads
  agent:
    workspace: ./.agentscope/workspace

logging:
  file:
    name: ./logs/claw-agent.log
  level:
    com.claw.agent: WARN
```

## 5. 运行时目录说明

| 目录 | 用途 | 注意事项 |
|------|------|----------|
| `data/uploads/` | 用户上传文件存储 | 按用户隔离，需持久化备份 |
| `.agentscope/workspace/` | Agent 工作区 | 包含人格/知识/技能定义，是 Agent 能力的 source of truth |
| `logs/` | 应用日志 | 可选，配置 `logging.file.name` 后启用 |

**重要**：以上目录均为运行时生成，升级版本时不要删除，否则会导致数据丢失。

## 6. 安全加固（生产必配）

> 平台采用**纵深防御**策略：应用层规则 + 系统层权限 + 容器层隔离，即使 Agent 被越狱或提示词注入，也无法实际写入源码。

### 6.1 应用层防护（已内置，无需额外配置）

| 机制 | 说明 |
|------|------|
| **AGENTS.md 人格规则** | 工作区人格文件明确禁止修改源码，列出受保护目录和拒绝话术 |
| **BASE_SYS_PROMPT 系统提示词** | 每个会话强制注入安全红线，Agent 启动时加载 |
| **HITL 人工确认** | `write_file` 工具调用必须经用户审批才能执行 |

**受保护目录清单**：
```
backend/src/              ← 后端 Java 源码
frontend/src/             ← 前端 TypeScript/React 源码
pom.xml / package.json    ← 构建配置
.git/                     ← 版本控制
.env / application.yml    ← 密钥与配置
```

**Agent 合法操作范围**：`workspace/`、`notes/`、`knowledge/`、`skills/`、`subagents/`

### 6.2 文件系统权限（Linux 生产环境）

```bash
# 1. 创建专用运行用户（禁止 root 运行）
useradd -r -s /bin/false claw-agent

# 2. 源码目录设为只读（555 = r-xr-xr-x）
chown -R root:root /opt/claw-agent/backend/src /opt/claw-agent/frontend/src
chmod -R 555 /opt/claw-agent/backend/src /opt/claw-agent/frontend/src

# 3. 构建配置文件只读
chmod 444 /opt/claw-agent/backend/pom.xml
chmod 444 /opt/claw-agent/frontend/package.json

# 4. 工作区目录授权给运行用户（Agent 合法操作范围）
chown -R claw-agent:claw-agent /opt/claw-agent/.agentscope/workspace
chmod -R 755 /opt/claw-agent/.agentscope/workspace

# 5. 上传目录授权
chown -R claw-agent:claw-agent /opt/claw-agent/backend/data/uploads
chmod -R 755 /opt/claw-agent/backend/data/uploads

# 6. 日志目录授权
chown -R claw-agent:claw-agent /opt/claw-agent/backend/logs
chmod -R 755 /opt/claw-agent/backend/logs
```

**权限说明**：
- `555`（r-xr-xr-x）：所有人可读可执行，无人可写 → 源码防篡改
- `755`（rwxr-xr-x）：所有者可读写执行，其他人只读 → 工作区可写
- `444`（r--r--r--）：所有人只读 → 配置文件防篡改

### 6.3 Docker 容器隔离（推荐）

```dockerfile
# Dockerfile 示例
FROM openjdk:17-slim

# 创建非 root 用户
RUN useradd -r -s /bin/false claw-agent

WORKDIR /opt/claw-agent

# 源码打入镜像（运行时只读）
COPY backend/target/backend-1.0.0-SNAPSHOT.jar ./app.jar
COPY backend/src ./src              # 只读，Agent 无法修改
COPY frontend/.next ./.next         # 只读

# 工作区用 Volume 挂载（可写）
VOLUME ["/opt/claw-agent/.agentscope/workspace"]
VOLUME ["/opt/claw-agent/data/uploads"]

# 非 root 用户运行
USER claw-agent

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**docker-compose.yml 示例**：
```yaml
version: '3.8'
services:
  backend:
    build: .
    ports:
      - "8080:8080"
    volumes:
      - workspace_data:/opt/claw-agent/.agentscope/workspace
      - upload_data:/opt/claw-agent/data/uploads
    environment:
      - CLAW_JWT_SECRET=${CLAW_JWT_SECRET}
      - MYSQL_HOST=mysql
      - MYSQL_PASSWORD=${MYSQL_PASSWORD}
    restart: unless-stopped

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: claw_agent
    volumes:
      - mysql_data:/var/lib/mysql
    restart: unless-stopped

volumes:
  workspace_data:
  upload_data:
  mysql_data:
```

**容器安全要点**：
- 源码通过 `COPY` 打入镜像层，运行时不可修改
- 工作区和上传目录用 `VOLUME` 挂载，独立持久化
- 容器内非 root 用户运行，限制文件操作权限
- 数据库密码通过环境变量注入，不写入镜像

### 6.4 网络层安全

```bash
# 1. 防火墙只开放必要端口
ufw allow 22/tcp    # SSH
ufw allow 80/tcp    # HTTP
ufw allow 443/tcp   # HTTPS
ufw deny 8080/tcp   # 后端端口不直接暴露，经 Nginx 代理
ufw enable

# 2. Nginx 反向代理（隐藏后端端口）
# 见 deploy/nginx/claw-agent.conf

# 3. 启用 HTTPS（Let's Encrypt 免费证书）
certbot --nginx -d your-domain.com
```

### 6.5 密钥管理

**禁止事项**：
- ❌ 不要将 `.env`、`application.yml` 中的密钥提交到 Git
- ❌ 不要在代码中硬编码 API Key、数据库密码
-  不要将 JWT Secret 使用默认值

**正确做法**：
```bash
# 1. 生产环境变量文件（.env，加入 .gitignore）
CLAW_JWT_SECRET=your-256-bit-random-secret-here
MYSQL_PASSWORD=strong-password-here
REDIS_PASSWORD=redis-password-here

# 2. 启动时加载
source .env
java -jar app.jar

# 3. 或使用 Docker secrets
docker secret create jwt_secret .env.jwt_secret
```

### 6.6 安全审计

```bash
# 1. 定期检查文件权限
find /opt/claw-agent/backend/src -writable -type f
# 应返回空（无文件可写）

# 2. 检查运行用户
ps aux | grep java
# 应显示 claw-agent 用户，非 root

# 3. 查看 HITL 审批记录
# 登录平台 → 系统管理 → 审计日志 → 筛选「工具调用」

# 4. 监控异常文件修改
auditctl -w /opt/claw-agent/backend/src -p wa -k source_code_change
ausearch -k source_code_change
```

---

## 7. 运维操作

### 7.1 版本升级

```bash
# 1. 备份数据
./deploy/scripts/backup-db.sh

# 2. 停止服务
./deploy/scripts/stop-all.sh

# 3. 替换 JAR 和前端产物
cp new-backend.jar backend/target/backend-1.0.0-SNAPSHOT.jar
cd frontend && npm install && npm run build

# 4. 启动服务
./deploy/scripts/start-backend.sh prod
./deploy/scripts/start-frontend.sh

# 5. 检查状态
./deploy/scripts/status.sh
```

### 7.2 日志查看

```bash
# 实时查看后端日志
tail -f backend/logs/claw-backend.log

# 搜索错误日志
grep -i error backend/logs/claw-backend.log | tail -50

# 查看 Nginx 访问日志
tail -f /var/log/nginx/access.log
```

## 8. 健康检查

### 8.1 接口探活

```bash
# 后端健康检查（无需登录）
curl -f http://localhost:8080/actuator/health || echo "Backend unhealthy"

# 前端健康检查
curl -f http://localhost:3000 -o /dev/null || echo "Frontend unhealthy"
```

### 8.2 进程监控

```bash
# 使用状态检查脚本
./deploy/scripts/status.sh

# 或手动检查
ps aux | grep claw-backend
ss -tlnp | grep -E '8080|3000'
```

## 9. 常见问题

### Q1: 首次启动报数据库连接失败

确认 MySQL 已启动且数据库已创建：
```sql
SHOW DATABASES LIKE 'claw_agent';
```

### Q2: Redis 连接失败是否影响使用

不影响。项目支持 Redis 自动降级，`claw.redis.enabled=auto` 时会尝试连接，失败后自动降级为本地 JSON 文件存储。

### Q3: 上传文件找不到

检查 `claw.upload.dir` 配置，默认为 `./data/uploads`（相对于 JAR 运行目录）。确保目录有写权限。

### Q4: SSE 流式输出中断

Nginx 代理需关闭缓冲：
```nginx
proxy_buffering off;
proxy_cache off;
```

### Q5: Agent 试图修改源码怎么办

平台已内置三层防护（人格规则 + 系统提示词 + HITL 审批），Agent 无法直接修改源码。如仍出现异常行为：

```bash
# 1. 检查文件权限是否正确
ls -la /opt/claw-agent/backend/src/
# 应显示 root:root 555（只读）

# 2. 检查运行用户
ps aux | grep java
# 应显示 claw-agent 用户，非 root

# 3. 查看 HITL 审批记录
# 登录平台 → 系统管理 → 审计日志 → 筛选「工具调用」

# 4. 如权限被篡改，立即修复
chown -R root:root /opt/claw-agent/backend/src
chmod -R 555 /opt/claw-agent/backend/src
```

---

**文档维护**：Claw Agent Team  
**最后更新**：2026-09-01
