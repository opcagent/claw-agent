#!/bin/bash
# ============================================================
# claw-agent 服务状态检查
# 用法: ./deploy/scripts/status.sh
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"

check_app() {
    local app_dir="$1"
    local app_name="$2"
    local port="$3"
    local pid_file="$app_dir/$app_name.pid"
    
    echo -n "[$app_name] "
    
    if [ ! -f "$pid_file" ]; then
        echo "未运行 (无 PID 文件)"
        return 1
    fi
    
    PID=$(cat "$pid_file")
    
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "未运行 (PID: $PID 已失效)"
        rm -f "$pid_file"
        return 1
    fi
    
    # 检查端口
    if command -v ss &> /dev/null; then
        if ss -tlnp | grep -q ":$port "; then
            echo "运行中 (PID: $PID, 端口: $port)"
            return 0
        else
            echo "运行中 (PID: $PID, 端口 $port 未监听)"
            return 0
        fi
    else
        echo "运行中 (PID: $PID)"
        return 0
    fi
}

echo "=========================================="
echo "  claw-agent 服务状态"
echo "=========================================="

BACKEND_OK=true
FRONTEND_OK=true

check_app "$PROJECT_DIR/backend" "claw-backend" 8080 || BACKEND_OK=false
check_app "$PROJECT_DIR/frontend" "claw-frontend" 3000 || FRONTEND_OK=false

echo "=========================================="

# 健康检查
echo ""
echo "健康检查:"

# 后端
if $BACKEND_OK; then
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "[Backend] 健康检查通过"
    else
        echo "[Backend] 健康检查失败"
    fi
fi

# 前端
if $FRONTEND_OK; then
    if curl -sf http://localhost:3000 -o /dev/null 2>&1; then
        echo "[Frontend] 健康检查通过"
    else
        echo "[Frontend] 健康检查失败"
    fi
fi
