-- ============================================================
-- V12__button_permissions.sql
-- Flyway 迁移脚本：菜单/部门/租户/用户/角色五模块增删改（含授权）按钮权限点
--   用户模块 2011-2014 已在 V4 落库，本脚本补齐其余模块的 F 型按钮；
--   权限标识沿用若依「模块:对象:动作」格式，动作取 add / edit / remove / resetPwd / grant
-- 授权：平台管理员（角色1）全量；租户管理员（角色2）除租户模块外全量
-- 规约：迁移脚本只增不改，后续变更请新增 V13__xxx.sql
-- ============================================================

-- ------------------------------------------------------------
-- 用户管理：补「分配角色」按钮（其余四按钮 V4 已存在）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2015, 201, '分配角色', 'F', 5, 'system:user:grant', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2015);

-- ------------------------------------------------------------
-- 角色管理：增 / 改 / 删 / 菜单授权
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2021, 202, '角色新增', 'F', 1, 'system:role:add', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2021);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2022, 202, '角色修改', 'F', 2, 'system:role:edit', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2022);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2023, 202, '角色删除', 'F', 3, 'system:role:remove', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2023);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2024, 202, '菜单授权', 'F', 4, 'system:role:grant', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2024);

-- ------------------------------------------------------------
-- 菜单管理：增 / 改 / 删 / 关联角色（菜单为平台级数据，权限点仅供平台管理员授权树展示）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2031, 203, '菜单新增', 'F', 1, 'system:menu:add', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2031);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2032, 203, '菜单修改', 'F', 2, 'system:menu:edit', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2032);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2033, 203, '菜单删除', 'F', 3, 'system:menu:remove', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2033);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2034, 203, '关联角色', 'F', 4, 'system:menu:grant', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2034);

-- ------------------------------------------------------------
-- 部门管理：增 / 改 / 删
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2041, 204, '部门新增', 'F', 1, 'system:dept:add', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2041);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2042, 204, '部门修改', 'F', 2, 'system:dept:edit', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2042);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2043, 204, '部门删除', 'F', 3, 'system:dept:remove', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2043);

-- ------------------------------------------------------------
-- 租户管理：增 / 改 / 删（平台管理员专属，不授权租户管理员）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2051, 205, '租户新增', 'F', 1, 'system:tenant:add', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2051);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2052, 205, '租户修改', 'F', 2, 'system:tenant:edit', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2052);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2053, 205, '租户删除', 'F', 3, 'system:tenant:remove', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2053);

-- ------------------------------------------------------------
-- 角色授权：平台管理员（角色1）全量；租户管理员（角色2）除租户模块外全量
--   （租户模块以 system:tenant: 前缀整体排除，覆盖 205 菜单与新增按钮）
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.id FROM sys_menu m
WHERE (m.perms IS NULL OR m.perms NOT LIKE 'system:tenant:%')
  AND m.id <> 205
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id);

-- ------------------------------------------------------------
-- 存量角色回填：凡已持有模块父菜单（201-204）的角色，同步补齐其下全部 F 按钮
--   覆盖其他租户动态创建的租户管理员/自定义角色（创建时 V12 按钮尚不存在，
--   不回填会被方法级按钮鉴权拦死）；租户按钮不回填（平台管理员专属）
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, btn.id
FROM sys_role_menu rm
         INNER JOIN sys_menu btn ON btn.parent_id = rm.menu_id AND btn.menu_type = 'F'
WHERE rm.menu_id IN (201, 202, 203, 204)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = btn.id);
