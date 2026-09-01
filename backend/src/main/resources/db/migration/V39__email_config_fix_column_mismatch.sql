-- V39__email_config_fix_column_mismatch.sql
-- email_config 表修复：列名、类型、缺失列全面对齐实体
--
-- 背景：V35 建表时列名与 EmailConfig 实体不匹配，V38 仅补齐审计字段，
-- 仍存在以下问题：
--   1. use_ssl / use_tls → 实体映射为 smtp_use_ssl / smtp_use_tls
--   2. user_id 为 BIGINT，实体为 String（格式：租户编码_自增序号）
--   3. 缺少 remark 列
--   4. 审计字段（V38 添加）需要幂等保证
--
-- 注意：全部使用动态 SQL，兼容 MySQL 5.7 / 8.0 低版本（不支持 IF NOT EXISTS 语法）

-- ============================================================
-- Part 1: 重命名列（use_ssl → smtp_use_ssl, use_tls → smtp_use_tls）
-- ============================================================

SET @has_use_ssl = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_config' AND COLUMN_NAME = 'use_ssl');
SET @has_use_tls = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_config' AND COLUMN_NAME = 'use_tls');

SET @sql_rename_ssl = IF(@has_use_ssl > 0,
    'ALTER TABLE email_config RENAME COLUMN use_ssl TO smtp_use_ssl', 'SELECT 1');
SET @sql_rename_tls = IF(@has_use_tls > 0,
    'ALTER TABLE email_config RENAME COLUMN use_tls TO smtp_use_tls', 'SELECT 1');

PREPARE stmt FROM @sql_rename_ssl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

PREPARE stmt FROM @sql_rename_tls;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- Part 2: 修改 user_id 类型 BIGINT → VARCHAR(64)
-- ============================================================

SET @uid_type = (SELECT DATA_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_config' AND COLUMN_NAME = 'user_id');

SET @sql_uid = IF(@uid_type = 'bigint',
    'ALTER TABLE email_config MODIFY COLUMN user_id VARCHAR(64) NOT NULL COMMENT ''用户ID（格式：租户编码_自增序号）''',
    'SELECT 1');

PREPARE stmt FROM @sql_uid;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- Part 3: 添加缺失的 remark 列
-- ============================================================

SET @has_remark = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_config' AND COLUMN_NAME = 'remark');

SET @sql_remark = IF(@has_remark = 0,
    'ALTER TABLE email_config ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT ''备注说明'' AFTER is_default',
    'SELECT 1');

PREPARE stmt FROM @sql_remark;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- Part 4: 补齐审计字段（幂等，V38 若已执行则跳过）
-- ============================================================

SET @has_creator = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_config' AND COLUMN_NAME = 'creator');
SET @has_updater = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_config' AND COLUMN_NAME = 'updater');
SET @has_creator_id = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_config' AND COLUMN_NAME = 'creator_id');
SET @has_updater_id = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_config' AND COLUMN_NAME = 'updater_id');

SET @sql_creator = IF(@has_creator = 0,
    'ALTER TABLE email_config ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT ''创建人'' AFTER update_time',
    'SELECT 1');
SET @sql_updater = IF(@has_updater = 0,
    'ALTER TABLE email_config ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT ''修改人'' AFTER creator',
    'SELECT 1');
SET @sql_creator_id = IF(@has_creator_id = 0,
    'ALTER TABLE email_config ADD COLUMN creator_id VARCHAR(64) DEFAULT NULL COMMENT ''创建人ID'' AFTER updater',
    'SELECT 1');
SET @sql_updater_id = IF(@has_updater_id = 0,
    'ALTER TABLE email_config ADD COLUMN updater_id VARCHAR(64) DEFAULT NULL COMMENT ''修改人ID'' AFTER creator_id',
    'SELECT 1');

PREPARE stmt FROM @sql_creator;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

PREPARE stmt FROM @sql_updater;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

PREPARE stmt FROM @sql_creator_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

PREPARE stmt FROM @sql_updater_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
