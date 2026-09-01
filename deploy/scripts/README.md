# claw-agent 运维脚本

本目录包含 claw-agent 平台的部署与运维脚本。

## 脚本列表

### Linux / macOS (Bash)

| 脚本 | 说明 | 用法 |
|------|------|------|
| `start-backend.sh` | 启动后端服务 | `./deploy/scripts/start-backend.sh [prod\|dev]` |
| `start-frontend.sh` | 启动前端服务 | `./deploy/scripts/start-frontend.sh [port]` |
| `stop-all.sh` | 停止所有服务 | `./deploy/scripts/stop-all.sh` |
| `status.sh` | 查看服务状态 | `./deploy/scripts/status.sh` |
| `backup-db.sh` | 数据库备份 | `./deploy/scripts/backup-db.sh [backup_dir]` |

### Windows (PowerShell)

| 脚本 | 说明 | 用法 |
|------|------|------|
| `start-backend.ps1` | 启动后端服务 | `.\deploy\scripts\start-backend.ps1 [-Profile prod]` |
| `start-frontend.ps1` | 启动前端服务 | `.\deploy\scripts\start-frontend.ps1 [-Port 3000]` |
| `stop-all.ps1` | 停止所有服务 | `.\deploy\scripts\stop-all.ps1` |
| `status.ps1` | 查看服务状态 | `.\deploy\scripts\status.ps1` |

## 快速开始

### Linux / macOS

```bash
# 赋予执行权限（首次）
chmod +x deploy/scripts/*.sh

# 设置环境变量
export CLAW_JWT_SECRET="your-production-secret"
export MYSQL_PASSWORD="your-db-password"

# 启动所有服务
./deploy/scripts/start-backend.sh prod
./deploy/scripts/start-frontend.sh

# 查看状态
./deploy/scripts/status.sh

# 停止所有服务
./deploy/scripts/stop-all.sh
```

### Windows

```powershell
# 设置环境变量
$env:CLAW_JWT_SECRET = "your-production-secret"
$env:MYSQL_PASSWORD = "your-db-password"

# 启动后端
.\deploy\scripts\start-backend.ps1 -Profile prod

# 启动前端
.\deploy\scripts\start-frontend.ps1 -Port 3000

# 查看状态
.\deploy\scripts\status.ps1

# 停止所有服务
.\deploy\scripts\stop-all.ps1
```

## 环境变量

启动前需设置以下环境变量：

| 变量 | 说明 | 示例 |
|------|------|------|
| `CLAW_JWT_SECRET` | JWT 签名密钥（生产必改） | `your-production-secret-key` |
| `MYSQL_HOST` | MySQL 主机 | `localhost` 或 `192.168.1.100` |
| `MYSQL_PASSWORD` | MySQL 密码 | `your-db-password` |
| `REDIS_HOST` | Redis 主机（可选） | `localhost` |
| `BACKEND_URL` | 前端连接后端的地址 | `http://localhost:8080` |

## 脚本功能说明

### start-backend

- 检查是否已有实例运行
- 自动创建 `data/uploads` 和 `logs` 目录
- 若 JAR 不存在则自动构建
- 后台运行并记录 PID
- 默认激活 `prod` Spring Profile

### start-frontend

- 检查是否已有实例运行
- 自动安装依赖（若 `node_modules` 不存在）
- 自动构建（若 `.next` 不存在）
- 支持自定义端口（默认 3000）

### stop-all

- 优雅停止服务（先发送 SIGTERM）
- 等待 30 秒后强制终止（Linux）
- 清理 PID 文件

### status

- 检查进程是否存活
- 检查端口监听状态
- 调用健康检查接口（`/actuator/health`）

### backup-db

- 使用 `mysqldump` 备份数据库
- 压缩为 `.sql.gz` 格式
- 自动清理 30 天前的旧备份

## 注意事项

1. **PID 文件管理**：脚本使用 PID 文件跟踪进程状态，不要手动删除
2. **日志轮转**：脚本未实现日志轮转，生产环境建议配置 `logrotate`（Linux）
3. **权限问题**：Linux 下确保脚本有执行权限（`chmod +x`）
4. **端口冲突**：确保 8080（后端）和 3000（前端）端口未被占用
5. **数据库备份**：建议配置定时任务（cron / Task Scheduler）定期执行备份
