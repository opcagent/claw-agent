-- ============================================================
-- V57__config_menu_perms.sql
-- 模型与能力（301）页面内部侧边栏 tab 接入权限系统
--
-- 背景：
--   config 页面的 7 个 tab 之前用硬编码 adminOnly / minRole 控制可见性，
--   无法通过菜单管理页面灵活配置角色授权。
-- 修复：
--   在菜单 301 下新增 7 个 F 类型按钮权限点，前端按权限标识过滤 tab，
--   管理员可在「菜单权限」页面自由调整角色与 tab 的关联。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 新增 F 类型权限点（parent_id = 301）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 30101, 301, '模型提供商', 'F', 1, 'agent:model:view', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 30101);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 30102, 301, '运行参数', 'F', 2, 'agent:param:view', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 30102);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 30103, 301, '工具集管理', 'F', 3, 'agent:tool:view', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 30103);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 30104, 301, '搜索引擎', 'F', 4, 'agent:search:view', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 30104);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 30105, 301, 'MCP 服务器', 'F', 5, 'agent:mcp:view', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 30105);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 30106, 301, '技能管理', 'F', 6, 'agent:skill:view', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 30106);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 30107, 301, '平台配置', 'F', 7, 'agent:system:view', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 30107);

-- ------------------------------------------------------------
-- 2. 角色授权（与 V55 重建后的授权对齐）
--    admin(1)：全部 F 类型
--    tenant_admin(2)：除工具集管理(30103)外全部（含平台配置）
--    common(3)：模型提供商(30101) + 运行参数(30102)
-- ------------------------------------------------------------

-- admin：全部
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (30101, 30102, 30103, 30104, 30105, 30106, 30107)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = m.id);

-- tenant_admin：模型提供商 + 运行参数 + 搜索引擎 + MCP + 技能 + 平台配置
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.id FROM sys_menu m
WHERE m.id IN (30101, 30102, 30104, 30105, 30106, 30107)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = m.id);

-- common：模型提供商(30101) + 运行参数(30102) + 租户空间(205，只读查看所属租户)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.id FROM sys_menu m
WHERE m.id IN (30101, 30102, 205)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3 AND menu_id = m.id);
