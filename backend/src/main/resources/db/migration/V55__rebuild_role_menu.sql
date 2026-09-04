-- ============================================================
-- V55__rebuild_role_menu.sql
-- 全面重建角色-菜单授权
--
-- 背景：
--   1. 历次增量迁移导致 sys_role_menu 累积了大量错误授权
--   2. V34 Token 统计菜单 status='0'(禁用) 且 parent_id=1(应为 2)
--   3. V48 渠道管理菜单因引用旧名 '系统管理'(V20 已改名 '平台治理') 未创建成功
--   4. admin 的 listMyMenus 短路返回全量菜单，绕过了 role_menu 授权
--
-- 修复：
--   1. 修复 Token 统计菜单：status=1, parent_id=2
--   2. 补建渠道管理菜单（如不存在）
--   3. 清空并重建 sys_role_menu
-- ============================================================

-- ============================================================
-- 1. 修复 Token 统计菜单（status 与 parent_id）
-- ============================================================
UPDATE sys_menu
SET status = 1,
    parent_id = 2,
    update_time = NOW()
WHERE id = 9000;

-- ============================================================
-- 2. 补建渠道管理菜单（V48 因引用旧名 '系统管理' 未成功创建）
-- ============================================================
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, icon, menu_type, visible, status, perms)
SELECT '渠道管理', 2, 20, '/system/channels', 'link', 'C', 1, 1, 'system:channel:list'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:channel:list' AND menu_type = 'C');

-- 渠道管理按钮权限（挂在刚创建的渠道管理菜单下）
INSERT INTO sys_menu (menu_name, parent_id, order_num, perms, menu_type, visible, status)
SELECT '渠道新增', m.id, 1, 'system:channel:add', 'F', 1, 1
FROM sys_menu m WHERE m.perms = 'system:channel:list' AND m.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:channel:add' AND menu_type = 'F');

INSERT INTO sys_menu (menu_name, parent_id, order_num, perms, menu_type, visible, status)
SELECT '渠道编辑', m.id, 2, 'system:channel:edit', 'F', 1, 1
FROM sys_menu m WHERE m.perms = 'system:channel:list' AND m.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:channel:edit' AND menu_type = 'F');

INSERT INTO sys_menu (menu_name, parent_id, order_num, perms, menu_type, visible, status)
SELECT '渠道删除', m.id, 3, 'system:channel:remove', 'F', 1, 1
FROM sys_menu m WHERE m.perms = 'system:channel:list' AND m.menu_type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:channel:remove' AND menu_type = 'F');

-- ============================================================
-- 3. 清空并重建 sys_role_menu
-- ============================================================

-- 清空旧授权数据
DELETE FROM sys_role_menu;

-- ------------------------------------------------------------
-- admin（role_id=1，平台管理员）：全部启用菜单
-- 平台管理员拥有所有菜单入口，后端通过 isAdmin() 旁路实现
-- 跨租户数据访问；菜单层面不做限制
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.status = 1
  AND m.menu_type IN ('M', 'C', 'F');

-- ------------------------------------------------------------
-- tenant_admin（role_id=2，租户管理员）：除租户空间外全部
-- 排除：
--   205 租户空间 —— 平台管理员专属（创建/管理租户）
-- 包含（后端 Service 层按 tenantId 过滤，只看本租户数据）：
--   206 审计日志 —— 本租户操作/登录日志
--   208 在线监控 —— 本租户在线用户
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.id
FROM sys_menu m
WHERE m.status = 1
  AND m.menu_type IN ('M', 'C', 'F')
  AND m.id NOT IN (205);

-- ------------------------------------------------------------
-- common（role_id=3，普通用户）：AI 对话 + 自助配置 + 查看类功能
-- 包含：
--   AI 工作台(1) + 智能对话(101)         —— 核心对话
--   平台治理(2) + Token 统计(9000)       —— 查看自己的 Token 用量
--   人格预设(102)                        —— 管理自己的预设模板
--   自动化流水线(4)                      —— 管理自己的流水线
--   智能体引擎(3) + 模型与能力(301)      —— 配置自己的模型（USER scope）
-- 不含 F 类型按钮权限（仅查看/使用，不可管理他人资源）
-- 不含平台治理下的管理页面（201-208 等），目录仅作导航容器
-- ------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.id
FROM sys_menu m
WHERE m.status = 1
  AND m.id IN (1, 101, 2, 9000, 102, 4, 3, 301);
