-- V8: 日志管理菜单（206）向平台管理员（角色1）与租户管理员（角色2）授权，
-- 与 V4 的菜单授权惯例保持一致，保证后续基于权限点（system:log:list）的按钮级鉴权可用。

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 206 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 206);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 206 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 206);
