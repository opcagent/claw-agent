-- ------------------------------------------------------------
-- V43：预设模板市场功能
-- 用户/租户可将自定义预设发布到市场（PLATFORM 作用域），
-- 其他用户可浏览并使用，使用次数自动统计。
-- ------------------------------------------------------------

-- 发布状态字段
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_preset' AND COLUMN_NAME = 'published');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE agent_preset ADD COLUMN published TINYINT NOT NULL DEFAULT 0 COMMENT ''是否发布到市场：0 否 1 是'' AFTER enabled',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 发布名称（市场展示用，可与 agent_name 不同）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_preset' AND COLUMN_NAME = 'publish_name');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE agent_preset ADD COLUMN publish_name VARCHAR(128) DEFAULT NULL COMMENT ''发布名称（市场展示用）'' AFTER published',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 发布描述
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_preset' AND COLUMN_NAME = 'publish_desc');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE agent_preset ADD COLUMN publish_desc VARCHAR(512) DEFAULT NULL COMMENT ''发布描述（市场展示用）'' AFTER publish_name',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 使用次数
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_preset' AND COLUMN_NAME = 'use_count');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE agent_preset ADD COLUMN use_count INT NOT NULL DEFAULT 0 COMMENT ''市场使用次数'' AFTER publish_desc',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 作者用户名（市场展示用，发布时从 owner 信息回填）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_preset' AND COLUMN_NAME = 'author_name');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE agent_preset ADD COLUMN author_name VARCHAR(64) DEFAULT NULL COMMENT ''作者名称（市场展示用）'' AFTER use_count',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 模板市场菜单（挂在「人格预设」同级或下级）
-- 先找到「人格预设」菜单的 parent_id，市场菜单挂在同一父级下
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, icon, menu_type, visible, status, perms)
SELECT '模板市场', parent_id, 2, '/marketplace', 'shopping', 'C', 1, 1, 'preset:marketplace:list'
FROM sys_menu
WHERE menu_name = '人格预设' AND menu_type = 'C'
LIMIT 1;

-- 角色授权：三个内置角色均可访问市场菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r, sys_menu m
WHERE m.menu_name = '模板市场' AND m.menu_type = 'C' AND m.path = '/marketplace'
  AND r.id IN (1, 2, 3)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
