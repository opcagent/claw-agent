-- ============================================================
-- V15__dict_button_permissions.sql
-- Flyway 迁移脚本：字典模块接入按钮级权限管理
--   背景：字典管理（207）V14 才补录菜单，增删改无 F 型按钮权限点，
--   角色授权树中无法按按钮粒度控制，写接口也未走方法级权限点鉴权。
--   本脚本登记三个按钮（新增/修改/删除，类型与数据共用），
--   并同步内置角色授权 + 存量持有 207 的角色回填。
--   脚本内部保持幂等（WHERE NOT EXISTS）
-- ============================================================

-- ------------------------------------------------------------
-- 1. 按钮权限点菜单（挂在 207 字典管理下，若依 模块:动作 格式）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2071, 207, '字典新增', 'F', 1, 'system:dict:add', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2071);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2072, 207, '字典修改', 'F', 2, 'system:dict:edit', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2072);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2073, 207, '字典删除', 'F', 3, 'system:dict:remove', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2073);

-- ------------------------------------------------------------
-- 2. 内置角色授权同步
-- ------------------------------------------------------------
-- 平台管理员（角色1）：全量
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (2071, 2072, 2073)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 租户管理员（角色2）：字典非租户专属模块，全量授予
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.id FROM sys_menu m
WHERE m.id IN (2071, 2072, 2073)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id);

-- ------------------------------------------------------------
-- 3. 存量角色回填：凡已持有字典管理菜单（207）的角色补齐三个按钮，
--    否则动态租户的管理员会被方法级按钮鉴权拦死
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, btn.id
FROM sys_role_menu rm
         INNER JOIN sys_menu btn ON btn.id IN (2071, 2072, 2073)
WHERE rm.menu_id = 207
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = btn.id);
