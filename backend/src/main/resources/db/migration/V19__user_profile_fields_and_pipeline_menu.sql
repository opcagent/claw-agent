-- ============================================================
-- V19__user_profile_fields_and_pipeline_menu.sql
-- Flyway 迁移脚本：
--   1. sys_user 补充联系方式字段：phone / email / gender
--   2. 编排流水线提升为可管理功能：新增一级菜单「流水线编排」（/pipelines）
-- 规约：迁移脚本只增不改；脚本内部保持幂等（information_schema 条件 DDL / WHERE NOT EXISTS）
-- ============================================================

-- ------------------------------------------------------------
-- 1. sys_user 补充字段（手机号 / 邮箱 / 性别）
-- ------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'phone');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN phone VARCHAR(20) DEFAULT NULL COMMENT ''手机号码'' AFTER nickname',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'email');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN email VARCHAR(128) DEFAULT NULL COMMENT ''电子邮箱'' AFTER phone',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'gender');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN gender TINYINT DEFAULT 0 COMMENT ''性别：0 未知 / 1 男 / 2 女'' AFTER email',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 流水线编排：一级目录（M 型无子菜单时前端导航点击直达自身 path）
--    菜单顺序：智能对话(1) → 预设模板(2) → 流水线编排(3) → 系统管理(4) → Agent 配置(5)
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 4, 0, '流水线编排', 'M', 3, '/pipelines', 'workflow', 'pipeline:use', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 4);

UPDATE sys_menu SET order_num = 4 WHERE id = 2;
UPDATE sys_menu SET order_num = 5 WHERE id = 3;

-- ------------------------------------------------------------
-- 3. 角色授权：三个平台内置角色全员可见（与预设模板同级的对话能力）
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 4 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 4);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 4 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 4);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, 4 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3 AND menu_id = 4);

-- 存量角色回填：持有「预设模板」（102）的角色视为已具备同类对话能力，补流水线
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 4
FROM sys_role_menu rm
WHERE rm.menu_id = 102
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 4);
