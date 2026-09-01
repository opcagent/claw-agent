-- V38__email_config_add_audit_columns.sql
-- email_config 表补齐四审计字段（creator / updater / creator_id / updater_id），
-- 使其符合项目数据库规约（所有业务表必备六审计字段，由 AuditMetaObjectHandler 自动填充）。
-- create_time / update_time 已在 V35 创建时存在，无需重复添加。
-- 注意：使用动态 SQL 兼容 MySQL 8.0 低版本（不支持 ADD COLUMN IF NOT EXISTS）

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
