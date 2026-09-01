# claw-agent 部署包

本目录包含 claw-agent 平台的完整部署资源。

## 目录结构

```
deploy/
├── README.md               # 本文件（部署总览）
├── guide.md                # 详细部署指南
├── scripts/                # 运维脚本
│   ├── README.md           # 脚本使用说明
│   ├── start-backend.sh    # 后端启动（Linux/macOS）
│   ├── start-backend.ps1   # 后端启动（Windows）
│   ├── start-frontend.sh   # 前端启动（Linux/macOS）
│   ├── start-frontend.ps1  # 前端启动（Windows）
│   ├── stop-all.sh         # 停止所有服务（Linux/macOS）
│   ├── stop-all.ps1        # 停止所有服务（Windows）
│   ├── status.sh           # 服务状态检查（Linux/macOS）
│   ├── status.ps1          # 服务状态检查（Windows）
│   └── backup-db.sh        # 数据库备份（Linux/macOS）
└── nginx/
    └── claw-agent.conf     # Nginx 反向代理配置示例
```

## 快速开始

### Linux / macOS

```bash
# 1. 赋予脚本执行权限
chmod +x deploy/scripts/*.sh

# 2. 设置环境变量
export CLAW_JWT_SECRET="your-production-secret"
export MYSQL_PASSWORD="your-db-password"

# 3. 启动服务
./deploy/scripts/start-backend.sh prod
./deploy/scripts/start-frontend.sh

# 4. 检查状态
./deploy/scripts/status.sh
```

### Windows PowerShell

```powershell
# 1. 设置环境变量
$env:CLAW_JWT_SECRET = "your-production-secret"
$env:MYSQL_PASSWORD = "your-db-password"

# 2. 启动服务
.\deploy\scripts\start-backend.ps1 -Profile prod
.\deploy\scripts\start-frontend.ps1

# 3. 检查状态
.\deploy\scripts\status.ps1
```

## 详细文档

- [guide.md](guide.md) - 完整部署指南（环境要求、构建流程、配置说明、运维操作）
- [scripts/README.md](scripts/README.md) - 脚本详细使用说明

## 生产部署清单

1. [ ] 准备服务器（JDK 17+、Node.js 18+、MySQL 8.0+、Redis 6.0+）
2. [ ] 创建数据库 `claw_agent`
3. [ ] 修改 `CLAW_JWT_SECRET`（生产必须替换）
4. [ ] 构建后端 JAR：`mvn clean package -DskipTests`
5. [ ] 构建前端：`npm install && npm run build`
6. [ ] 配置 Nginx（可选，参考 `nginx/claw-agent.conf`）
7. [ ] 启动服务并验证健康检查
