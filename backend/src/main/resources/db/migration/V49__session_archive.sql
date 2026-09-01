-- ------------------------------------------------------------
-- V49：会话归档功能
-- chat_session 增加 archived 字段，支持将会话归档（隐藏但保留），
-- 归档会话可从「归档」Tab 查看、取消归档或彻底删除。
-- ------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_session' AND COLUMN_NAME = 'archived');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE chat_session ADD COLUMN archived TINYINT NOT NULL DEFAULT 0 COMMENT ''是否归档：0 活跃 1 已归档'' AFTER summary',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为归档字段加索引，便于按归档状态过滤会话列表
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_session' AND INDEX_NAME = 'idx_user_archived');
SET @idx_ddl = IF(@idx_exists = 0,
    'ALTER TABLE chat_session ADD INDEX idx_user_archived (username, archived)',
    'SELECT 1');
PREPARE idx_stmt FROM @idx_ddl;
EXECUTE idx_stmt;
DEALLOCATE PREPARE idx_stmt;
