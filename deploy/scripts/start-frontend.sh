#!/bin/bash
# ============================================================
# claw-agent 前端启动脚本
# 用法: ./deploy/scripts/start-frontend.sh [port]
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
FRONTEND_DIR="$PROJECT_DIR/frontend"

APP_NAME="claw-frontend"
PID_FILE="$FRONTEND_DIR/$APP_NAME.pid"
LOG_DIR="$FRONTEND_DIR/logs"
LOG_FILE="$LOG_DIR/$APP_NAME.log"
PORT="${1:-3000}"

cd "$FRONTEND_DIR"

# 检查是否已运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "[$APP_NAME] 已在运行 (PID: $PID)"
        exit 1
    fi
    rm -f "$PID_FILE"
fi

# 创建日志目录
mkdir -p "$LOG_DIR"

# 检查依赖
if [ ! -d "node_modules" ]; then
    echo "[$APP_NAME] 安装依赖..."
    npm install --production=false
fi

# 检查构建产物
if [ ! -d ".next" ]; then
    echo "[$APP_NAME] 构建前端..."
    npm run build
fi

# 设置后端地址（生产环境直连或经 Nginx）
export BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"

# 启动应用
echo "[$APP_NAME] 启动中 (port=$PORT, backend=$BACKEND_URL)..."
nohup npm run start -- -p $PORT >> "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

sleep 2
if kill -0 "$(cat $PID_FILE)" 2>/dev/null; then
    echo "[$APP_NAME] 启动成功 (PID: $(cat $PID_FILE))"
    echo "[$APP_NAME] 访问: http://localhost:$PORT"
    echo "[$APP_NAME] 日志: tail -f $LOG_FILE"
else
    echo "[$APP_NAME] 启动失败，请检查日志: $LOG_FILE"
    exit 1
fi
