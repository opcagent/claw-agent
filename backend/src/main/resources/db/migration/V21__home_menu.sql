-- ============================================================
-- V21__home_menu.sql
-- Flyway 迁移脚本：新增一级菜单「首页」（/home 帮助中心）
--   背景：平台需要一个统一门户页：平台介绍、快速上手、功能导航，
--   新用户登录后先看到「怎么用、有什么」，再进入具体功能。
--   M 型目录无子菜单时前端导航点击直达自身 path（与预设/流水线同模式）。
--   菜单顺序：首页(1) → AI 工作台(2) → 人格预设(3) → 自动化流水线(4)
--             → 平台治理(5) → 智能体引擎(6)
-- 规约：迁移脚本只增不改；脚本内部保持幂等（NOT EXISTS 防重）
-- ============================================================

-- ------------------------------------------------------------
-- 1. 首页菜单（一级目录，icon=home，全员可见无需权限点）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 600, 0, '首页', 'M', 1, '/home', 'home', NULL, 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 600);

-- 2. 现有一级目录整体后移一位（仅顶层目录，不影响二级菜单排序）
UPDATE sys_menu SET order_num = order_num + 1
WHERE parent_id = 0 AND menu_type = 'M' AND id <> 600;

-- ------------------------------------------------------------
-- 3. 角色授权：三个平台内置角色全员可见（首页属基础门户，不设门槛）
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 600 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 600);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 600 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 600);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, 600 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3 AND menu_id = 600);

-- 存量角色回填：持有「智能对话」（101）的角色视为平台基础用户，补首页
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 600
FROM sys_role_menu rm
WHERE rm.menu_id = 101
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 600);
