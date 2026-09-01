-- ------------------------------------------------------------
-- V42：会话摘要字段
-- 用于跨会话记忆：每次对话结束后自动生成/更新摘要，
-- 新会话构建 Agent 时将近期摘要注入系统提示词，
-- 让 Agent 知道用户最近在做什么，实现跨会话上下文延续。
-- ------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_session' AND COLUMN_NAME = 'summary');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE chat_session ADD COLUMN summary VARCHAR(1024) DEFAULT NULL COMMENT ''会话摘要（跨会话记忆用）'' AFTER title',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
