-- ------------------------------------------------------------
-- V61__fix_agent_config_uk_scope_key.sql
-- 修复配置表唯一键：加入 owner_id
--
-- 背景:
--   agent_config.uk_scope_key(scope, tenant_id, config_key) 缺少 owner_id，
--   model_provider_config.uk_scope_provider(scope, tenant_id, provider) 同样缺少 owner_id，
--   导致同一租户内不同用户保存 USER 级配置时互相冲突（DuplicateKeyException）。
--   正确唯一键应包含 owner_id。
-- ------------------------------------------------------------

-- ============================================================
-- Part 1: agent_config — uk_scope_key 加入 owner_id
-- ============================================================

-- 1. 清理可能存在的重复数据（保留 id 最小的一条）
DELETE t1 FROM agent_config t1
INNER JOIN (
    SELECT scope, tenant_id, config_key, owner_id, MIN(id) AS keep_id
    FROM agent_config
    GROUP BY scope, tenant_id, config_key, owner_id
    HAVING COUNT(*) > 1
) t2 ON t1.scope = t2.scope
    AND t1.tenant_id = t2.tenant_id
    AND t1.config_key = t2.config_key
    AND (t1.owner_id = t2.owner_id OR (t1.owner_id IS NULL AND t2.owner_id IS NULL))
    AND t1.id > t2.keep_id;

-- 2. 删除旧唯一索引（COUNT(*) 确保子查询返回单值，避免 information_schema 多行问题）
SET @db = (SELECT DATABASE());
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'agent_config' AND INDEX_NAME = 'uk_scope_key');
SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE agent_config DROP INDEX uk_scope_key',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 创建包含 owner_id 的新唯一索引
SET @idx_exists2 = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'agent_config' AND INDEX_NAME = 'uk_scope_key');
SET @sql2 = IF(@idx_exists2 = 0,
    'ALTER TABLE agent_config ADD UNIQUE KEY uk_scope_key (scope, tenant_id, owner_id, config_key)',
    'SELECT 1');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- ============================================================
-- Part 2: model_provider_config — uk_scope_provider 加入 owner_id
-- ============================================================

-- 2.1 清理重复数据
DELETE t1 FROM model_provider_config t1
INNER JOIN (
    SELECT scope, tenant_id, provider, owner_id, MIN(id) AS keep_id
    FROM model_provider_config
    GROUP BY scope, tenant_id, provider, owner_id
    HAVING COUNT(*) > 1
) t2 ON t1.scope = t2.scope
    AND t1.tenant_id = t2.tenant_id
    AND t1.provider = t2.provider
    AND (t1.owner_id = t2.owner_id OR (t1.owner_id IS NULL AND t2.owner_id IS NULL))
    AND t1.id > t2.keep_id;

-- 2.2 删除旧唯一索引
SET @idx3_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'model_provider_config' AND INDEX_NAME = 'uk_scope_provider');
SET @sql3 = IF(@idx3_exists > 0,
    'ALTER TABLE model_provider_config DROP INDEX uk_scope_provider',
    'SELECT 1');
PREPARE stmt3 FROM @sql3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- 2.3 创建包含 owner_id 的新唯一索引
SET @idx4_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'model_provider_config' AND INDEX_NAME = 'uk_scope_provider');
SET @sql4 = IF(@idx4_exists = 0,
    'ALTER TABLE model_provider_config ADD UNIQUE KEY uk_scope_provider (scope, tenant_id, owner_id, provider)',
    'SELECT 1');
PREPARE stmt4 FROM @sql4;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;
