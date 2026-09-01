-- ============================================================
-- V24__online_monitor_menu.sql
-- Flyway 迁移脚本：新增二级菜单「在线监控」（/system/online）
--   背景：管理员需要实时掌握平台用户活跃情况（在线用户、最近活跃时间、访问 IP）。
--   挂「平台治理」(2) 之下，紧随审计日志；权限点 system:online:list。
--   仅管理员类角色可见（后端接口同时要求 tenant_admin 及以上）。
-- 规约：迁移脚本只增不改；脚本内部保持幂等（NOT EXISTS 防重）
-- ============================================================

-- ------------------------------------------------------------
-- 1. 在线监控菜单（平台治理下，order 8 紧随字典管理）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 208, 2, '在线监控', 'C', 8, '/system/online', 'monitor', 'system:online:list', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 208);

-- ------------------------------------------------------------
-- 2. 角色授权：内置平台管理员(1) / 默认租户管理员(2)；普通用户(3)不授权
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 208 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 208);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 208 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 208);

-- 存量角色回填：持有「审计日志」(206) 的角色视为管理角色，同步授予在线监控
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 208
FROM sys_role_menu rm
WHERE rm.menu_id = 206
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 208);
