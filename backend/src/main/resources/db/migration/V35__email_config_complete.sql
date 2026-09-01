-- V35__email_config_complete.sql
-- 邮箱配置功能完整实现(表结构 + 菜单 + 权限)
-- 
-- 背景: 将 V35(表结构) 和 V36(菜单权限) 合并为一个脚本
-- 目标: 减少迁移脚本数量,提高可维护性

-- ============================================================
-- Part 1: 创建 email_config 表
-- ============================================================

CREATE TABLE IF NOT EXISTS email_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    from_email VARCHAR(255) NOT NULL COMMENT '发件人邮箱',
    from_name VARCHAR(255) COMMENT '发件人名称',
    smtp_host VARCHAR(255) NOT NULL COMMENT 'SMTP服务器地址',
    smtp_port INT NOT NULL DEFAULT 587 COMMENT 'SMTP端口',
    smtp_username VARCHAR(255) NOT NULL COMMENT 'SMTP用户名',
    smtp_password VARCHAR(500) NOT NULL COMMENT 'SMTP密码(加密存储)',
    use_ssl BOOLEAN DEFAULT FALSE COMMENT '是否启用SSL',
    use_tls BOOLEAN DEFAULT TRUE COMMENT '是否启用TLS',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    is_default BOOLEAN DEFAULT FALSE COMMENT '是否为默认配置',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_tenant (user_id, tenant_id),
    INDEX idx_default (user_id, tenant_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户邮箱配置表';

-- ============================================================
-- Part 2: 添加邮箱配置菜单
-- ============================================================

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status, create_time, update_time)
SELECT 
    COALESCE((SELECT MAX(id) FROM sys_menu) + 1, 100),  -- 动态生成 ID
    2,  -- parent_id = 2 (系统管理)
    '邮箱配置',
    'C',  -- 菜单类型: C=菜单
    8,  -- order_num
    'email-config',  -- path (前端据此推导组件路径)
    'mail',  -- 图标
    'system:email-config:list',  -- 权限标识
    1,  -- visible: 1=显示
    1,  -- status: 1=启用
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'system:email-config:list'
);

-- ============================================================
-- Part 3: 分配菜单权限
-- ============================================================

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 
    r.id,
    m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE m.perms = 'system:email-config:list'
  AND r.role_key IN ('admin', 'tenant_admin', 'common')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm 
      WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- ============================================================
-- Part 4: 验证结果
-- ============================================================

-- 验证表是否创建成功
SELECT 
    TABLE_NAME,
    TABLE_COMMENT,
    CREATE_TIME
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'email_config';

-- 验证菜单是否创建成功
SELECT 
    id,
    parent_id,
    menu_name,
    menu_type,
    path,
    perms,
    visible
FROM sys_menu 
WHERE perms = 'system:email-config:list';

-- 验证权限分配情况
SELECT 
    r.role_key,
    m.menu_name,
    rm.create_time
FROM sys_role_menu rm
JOIN sys_role r ON rm.role_id = r.id
JOIN sys_menu m ON rm.menu_id = m.id
WHERE m.perms = 'system:email-config:list'
ORDER BY r.role_key;
