-- ============================================================
-- V59__tenant_feature_config.sql
-- 租户功能模块配置表
--
-- 功能：
-- 1. 平台管理员可为每个租户配置可用的功能模块（菜单）
-- 2. 租户管理员只能使用平台管理员配置的功能模块
-- 3. 未配置的租户默认拥有全部功能（向后兼容）
-- ============================================================

-- ------------------------------------------------------------
-- 1. 创建租户功能配置表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenant_feature (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id   BIGINT NOT NULL COMMENT '租户ID（关联 sys_tenant.id）',
    menu_id     BIGINT NOT NULL COMMENT '菜单/功能ID（关联 sys_menu.id）',
    enabled     TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用 / 0 禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator     VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    updater     VARCHAR(64) DEFAULT NULL COMMENT '修改人',
    creator_id  VARCHAR(64) DEFAULT NULL COMMENT '创建人ID',
    updater_id  VARCHAR(64) DEFAULT NULL COMMENT '修改人ID',
    UNIQUE KEY uk_tenant_menu (tenant_id, menu_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户功能模块配置表';

-- ------------------------------------------------------------
-- 2. 插入菜单管理功能到平台治理菜单下（供平台管理员配置租户功能）
--    假设平台治理菜单 ID 为 200（根据实际调整）
-- ------------------------------------------------------------
-- 注意：如果平台治理菜单 ID 不是 200，请先查询实际 ID
-- SELECT id, menu_name FROM sys_menu WHERE menu_name = '平台治理';

-- 插入租户功能配置菜单（平台管理员专用）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status, create_time)
SELECT 200, '租户功能配置', 'C', 9, 'tenant-feature', 'Settings', 'admin:tenantFeature:list', 1, 1, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name = '租户功能配置' AND parent_id = 200);

-- 获取刚插入的菜单 ID（用于后续按钮权限）
SET @tenant_feature_menu_id = LAST_INSERT_ID();

-- 插入按钮权限：查询
INSERT INTO sys_menu (parent_id, menu_name, menu_type, order_num, perms, visible, status, create_time)
SELECT @tenant_feature_menu_id, '功能配置查询', 'F', 1, 'admin:tenantFeature:query', 1, 1, NOW()
FROM DUAL
WHERE @tenant_feature_menu_id > 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name = '功能配置查询' AND parent_id = @tenant_feature_menu_id);

-- 插入按钮权限：保存
INSERT INTO sys_menu (parent_id, menu_name, menu_type, order_num, perms, visible, status, create_time)
SELECT @tenant_feature_menu_id, '功能配置保存', 'F', 2, 'admin:tenantFeature:save', 1, 1, NOW()
FROM DUAL
WHERE @tenant_feature_menu_id > 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name = '功能配置保存' AND parent_id = @tenant_feature_menu_id);
