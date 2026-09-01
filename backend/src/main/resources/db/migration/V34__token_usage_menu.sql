-- ------------------------------------------------------------
-- V34__token_usage_menu.sql
-- 添加 Token 使用统计菜单
-- 
-- 功能:
-- 1. 在"系统管理"下添加"Token 统计"菜单项
-- 2. 授予 USER 和 ADMIN 角色访问权限
-- ------------------------------------------------------------

-- ============================================================
-- 1. 查找"系统管理"父菜单 ID
-- ============================================================
-- 假设 sys_menu 中已有"系统管理"菜单 (通常 id=100 或类似)
-- 如果不存在,需要先创建

-- ============================================================
-- 2. 插入 Token 使用统计菜单
-- ============================================================
INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, order_num, 
    path, icon, perms, visible, status, create_time, update_time
) VALUES (
    9000, -- 新菜单 ID (确保不冲突)
    1,    -- 父菜单 ID (假设"系统管理"的 id=1,需根据实际情况调整)
    'Token 统计',
    'C',  -- 菜单类型: C=页面
    10,   -- 排序号
    '/token-usage',
    'bar-chart-2', -- Lucide 图标名
    'system:token-usage:view',
    1,    -- 可见
    '0',  -- 正常状态
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    update_time = NOW();

-- ============================================================
-- 3. 为 USER 角色授权 (假设 role_id=2 是普通用户)
-- ============================================================
INSERT INTO sys_role_menu (role_id, menu_id, create_time)
SELECT 2, 9000, NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 9000
);

-- ============================================================
-- 4. 为 ADMIN 角色授权 (假设 role_id=1 是管理员)
-- ============================================================
INSERT INTO sys_role_menu (role_id, menu_id, create_time)
SELECT 1, 9000, NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 9000
);

-- ============================================================
-- 验证插入结果
-- ============================================================
SELECT 
    m.id,
    m.menu_name,
    m.path,
    m.icon,
    m.perms,
    GROUP_CONCAT(r.role_key SEPARATOR ', ') AS granted_roles
FROM sys_menu m
LEFT JOIN sys_role_menu rm ON m.id = rm.menu_id
LEFT JOIN sys_role r ON rm.role_id = r.id
WHERE m.id = 9000
GROUP BY m.id;
