-- ============================================================
-- V14__menu_nav_alignment.sql
-- Flyway 迁移脚本：菜单数据与前端路由对齐，支撑「顶部一级 + 左侧二级」导航
--   1. path 字段统一改写为前端完整路由（导航由菜单数据驱动）
--   2. 「模型配置/运行参数」合并为「Agent 配置」单一菜单（前端为同一页面），原 302 隐藏停用
--   3. 补录缺失菜单：预设模板（102）、字典管理（207）
--   4. 角色授权同步：平台管理员全量、租户管理员除租户模块外全量、
--      普通用户补预设模板；存量角色按父级持有情况回填新菜单
--   脚本内部保持幂等（WHERE NOT EXISTS / 条件 UPDATE）
-- ============================================================

-- ------------------------------------------------------------
-- 1. path 对齐前端路由
-- ------------------------------------------------------------
UPDATE sys_menu SET path = '/'             WHERE id = 101;
UPDATE sys_menu SET path = '/system/user'   WHERE id = 201;
UPDATE sys_menu SET path = '/system/role'   WHERE id = 202;
UPDATE sys_menu SET path = '/system/menu'   WHERE id = 203;
UPDATE sys_menu SET path = '/system/dept'   WHERE id = 204;
UPDATE sys_menu SET path = '/system/tenant' WHERE id = 205;
UPDATE sys_menu SET path = '/system/log'    WHERE id = 206;

-- Agent 配置合并：301 承担页面入口，302（运行参数）为同一页面的分区，停用隐藏
UPDATE sys_menu SET menu_name = 'Agent 配置', path = '/system/config', order_num = 1
WHERE id = 301;
UPDATE sys_menu SET visible = 0, status = 0
WHERE id = 302;
-- 持有 302 授权的角色补齐 301，避免合并后丢入口
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 301
FROM sys_role_menu rm
WHERE rm.menu_id = 302
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 301);

-- ------------------------------------------------------------
-- 2. 补录缺失菜单
-- ------------------------------------------------------------
-- 预设模板（智能对话下，全员可用，权限点 preset:use 仅展示登记）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 102, 1, '预设模板', 'C', 2, '/presets', 'sparkles', 'preset:use', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 102);

-- 字典管理（系统管理下，权限 system:dict:list）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 207, 2, '字典管理', 'C', 7, '/system/dict', 'book', 'system:dict:list', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 207);

-- ------------------------------------------------------------
-- 3. 内置角色授权同步
-- ------------------------------------------------------------
-- 平台管理员（角色1）：全量
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.status = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 租户管理员（角色2）：除租户模块外全量
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.id FROM sys_menu m
WHERE m.status = 1
  AND (m.perms IS NULL OR m.perms NOT LIKE 'system:tenant:%')
  AND m.id <> 205
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id);

-- 普通用户（角色3）：补预设模板（与聊天同属对话能力）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, 102 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3 AND menu_id = 102);

-- ------------------------------------------------------------
-- 4. 存量角色回填（其他租户动态创建的角色）
--    持有「智能对话」目录（1）者补预设模板；持有「系统管理」目录（2）者补字典管理
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 102
FROM sys_role_menu rm
WHERE rm.menu_id = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 102);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 207
FROM sys_role_menu rm
WHERE rm.menu_id = 2
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 207);
