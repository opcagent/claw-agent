#!/bin/bash
# ============================================================
# claw-agent 停止所有服务
# 用法: ./deploy/scripts/stop-all.sh
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"

stop_app() {
    local app_dir="$1"
    local app_name="$2"
    local pid_file="$app_dir/$app_name.pid"
    
    if [ ! -f "$pid_file" ]; then
        echo "[$app_name] PID 文件不存在，跳过"
        return 0
    fi
    
    PID=$(cat "$pid_file")
    
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "[$app_name] 进程不存在 (PID: $PID)，清理 PID 文件"
        rm -f "$pid_file"
        return 0
    fi
    
    echo "[$app_name] 停止中 (PID: $PID)..."
    kill "$PID"
    
    # 等待进程退出（最多 30 秒）
    for i in {1..30}; do
        if ! kill -0 "$PID" 2>/dev/null; then
            echo "[$app_name] 已停止"
            rm -f "$pid_file"
            return 0
        fi
        sleep 1
    done
    
    # 强制终止
    echo "[$app_name] 强制终止 (PID: $PID)..."
    kill -9 "$PID" 2>/dev/null || true
    rm -f "$pid_file"
    echo "[$app_name] 已强制停止"
}

echo "=========================================="
echo "  claw-agent 服务停止"
echo "=========================================="

stop_app "$PROJECT_DIR/backend" "claw-backend"
stop_app "$PROJECT_DIR/frontend" "claw-frontend"

echo "=========================================="
echo "  所有服务已停止"
echo "=========================================="
