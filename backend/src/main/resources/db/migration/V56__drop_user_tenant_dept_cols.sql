-- V56: 从 sys_user 移除 tenant_id / dept_id（已迁移至 sys_user_tenant）
-- 背景：多租户改造后 tenant_id/dept_id 的 source of truth 为 sys_user_tenant，
--        sys_user 仅保留跨组织共享的用户基础属性，冗余字段清理以避免双写不一致。

-- 删除 idx_tenant 索引（tenant_id 列删除前必须先删索引）
SET @db = (SELECT DATABASE());
SET @idx_exists = (SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant');
SET @sql = IF(@idx_exists IS NOT NULL,
    'ALTER TABLE sys_user DROP INDEX idx_tenant',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除 tenant_id 列
SET @col_exists = (SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'tenant_id');
SET @sql = IF(@col_exists IS NOT NULL,
    'ALTER TABLE sys_user DROP COLUMN tenant_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除 dept_id 列
SET @col_exists2 = (SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'dept_id');
SET @sql2 = IF(@col_exists2 IS NOT NULL,
    'ALTER TABLE sys_user DROP COLUMN dept_id',
    'SELECT 1');
PREPARE stmt FROM @sql2;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
