#!/bin/bash
# ============================================================
# claw-agent 后端启动脚本
# 用法: ./deploy/scripts/start-backend.sh [prod|dev]
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
BACKEND_DIR="$PROJECT_DIR/backend"

APP_NAME="claw-backend"
JAR_FILE="backend-1.0.0-SNAPSHOT.jar"
PID_FILE="$BACKEND_DIR/$APP_NAME.pid"
LOG_DIR="$BACKEND_DIR/logs"
LOG_FILE="$LOG_DIR/$APP_NAME.log"

# 默认激活 prod 配置
PROFILE="${1:-prod}"

# JVM 参数（按服务器配置调整）
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR}"

# Spring 配置
SPRING_OPTS="--spring.profiles.active=$PROFILE"

cd "$BACKEND_DIR"

# 检查是否已运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "[$APP_NAME] 已在运行 (PID: $PID)"
        exit 1
    fi
    rm -f "$PID_FILE"
fi

# 创建必要目录
mkdir -p data/uploads "$LOG_DIR"

# 检查 JAR 是否存在
if [ ! -f "target/$JAR_FILE" ]; then
    echo "[$APP_NAME] JAR 文件不存在，开始构建..."
    mvn clean package -DskipTests -q
fi

# 启动应用
echo "[$APP_NAME] 启动中 (profile=$PROFILE)..."
nohup java $JAVA_OPTS -jar "target/$JAR_FILE" $SPRING_OPTS >> "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

sleep 2
if kill -0 "$(cat $PID_FILE)" 2>/dev/null; then
    echo "[$APP_NAME] 启动成功 (PID: $(cat $PID_FILE))"
    echo "[$APP_NAME] 日志: tail -f $LOG_FILE"
else
    echo "[$APP_NAME] 启动失败，请检查日志: $LOG_FILE"
    exit 1
fi
