#!/bin/bash
# ============================================================
# claw-agent 数据库备份脚本
# 用法: ./deploy/scripts/backup-db.sh [backup_dir]
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"

# 数据库配置（从环境变量读取或使用默认值）
DB_HOST="${MYSQL_HOST:-localhost}"
DB_PORT="${MYSQL_PORT:-3306}"
DB_NAME="${MYSQL_DB:-claw_agent}"
DB_USER="${MYSQL_USER:-root}"
DB_PASS="${MYSQL_PASSWORD:-}"

# 备份目录
BACKUP_DIR="${1:-$PROJECT_DIR/backup}"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_${DATE}.sql.gz"

# 创建备份目录
mkdir -p "$BACKUP_DIR"

echo "=========================================="
echo "  claw-agent 数据库备份"
echo "=========================================="
echo "数据库: $DB_NAME@$DB_HOST:$DB_PORT"
echo "备份文件: $BACKUP_FILE"
echo ""

# 执行备份
if [ -n "$DB_PASS" ]; then
    mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" \
        --single-transaction --routines --triggers \
        "$DB_NAME" | gzip > "$BACKUP_FILE"
else
    mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" \
        --single-transaction --routines --triggers \
        "$DB_NAME" | gzip > "$BACKUP_FILE"
fi

# 显示备份大小
BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "备份完成: $BACKUP_FILE ($BACKUP_SIZE)"

# 清理旧备份（保留最近 30 天）
echo ""
echo "清理 30 天前的备份..."
DELETED=$(find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +30 -delete -print | wc -l)
echo "已清理 $DELETED 个旧备份"

echo "=========================================="
