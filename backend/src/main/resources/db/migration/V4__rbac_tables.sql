-- ============================================================
-- V4__rbac_tables.sql
-- Flyway 迁移脚本：多租户 RBAC 权限体系
--   层级：租户(sys_tenant) -> 部门(sys_dept) -> 用户/员工(sys_user)
--         -> 角色(sys_role) -> 菜单/权限(sys_menu) -> 数据权限(sys_role.data_scope)
--   关联：sys_user_role（用户-角色）、sys_role_menu（角色-菜单/权限）
-- 数据权限（若依五档）：1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人
-- 规约：迁移脚本只增不改，后续变更请新增 V5__xxx.sql
-- ============================================================

-- ------------------------------------------------------------
-- 部门表：租户内树形组织（ancestors 存父链，如 0,100,101，便于"及以下"查询）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dept (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    tenant_id   BIGINT       NOT NULL                COMMENT '所属租户ID',
    parent_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '父部门ID（根为 0）',
    ancestors   VARCHAR(512) NOT NULL DEFAULT '0'    COMMENT '父链（逗号分隔，含根 0）',
    dept_name   VARCHAR(64)  NOT NULL                COMMENT '部门名称',
    order_num   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    leader      VARCHAR(64)  DEFAULT NULL            COMMENT '负责人用户名',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id),
    KEY idx_parent (tenant_id, parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部门表';

-- ------------------------------------------------------------
-- 角色表：数据权限由 data_scope + sys_role_dept 决定
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    tenant_id   BIGINT       NOT NULL                COMMENT '所属租户ID（平台超管角色租户为 0）',
    role_name   VARCHAR(64)  NOT NULL                COMMENT '角色名称',
    role_key    VARCHAR(64)  NOT NULL                COMMENT '角色权限字符串（如 admin / common，JWT 与鉴权使用）',
    role_sort   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    data_scope  TINYINT      NOT NULL DEFAULT 5      COMMENT '数据权限：1全部 2自定义 3本部门 4本部门及以下 5仅本人',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 禁用',
    remark      VARCHAR(255) DEFAULT NULL            COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_role_key (tenant_id, role_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- ------------------------------------------------------------
-- 菜单/权限表：M 目录 / C 菜单 / F 按钮；perms 为权限标识（如 system:user:add）
-- 菜单为平台级（租户共享），按角色授权
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    parent_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '父菜单ID（根为 0）',
    menu_name   VARCHAR(64)  NOT NULL                COMMENT '菜单名称',
    menu_type   CHAR(1)      NOT NULL DEFAULT 'C'    COMMENT '类型：M 目录 / C 菜单 / F 按钮',
    order_num   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    path        VARCHAR(255) DEFAULT NULL            COMMENT '路由地址 / 组件路径',
    icon        VARCHAR(128) DEFAULT NULL            COMMENT '菜单图标',
    perms       VARCHAR(128) DEFAULT NULL            COMMENT '权限标识（如 system:user:add）',
    visible     TINYINT      NOT NULL DEFAULT 1      COMMENT '是否显示：1 显示 / 0 隐藏',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单权限表';

-- ------------------------------------------------------------
-- 用户-角色关联表（多对多）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色关联表';

-- ------------------------------------------------------------
-- 角色-菜单关联表（多对多，权限点授权）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色菜单关联表';

-- ------------------------------------------------------------
-- 角色-部门关联表：data_scope=2（自定义）时生效
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role_dept (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    dept_id BIGINT NOT NULL COMMENT '部门ID',
    PRIMARY KEY (role_id, dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色部门关联表（自定义数据权限）';

-- ------------------------------------------------------------
-- sys_user 挂部门（幂等：MySQL 不支持 ADD COLUMN IF NOT EXISTS，用条件执行）
-- ------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'dept_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN dept_id BIGINT DEFAULT NULL COMMENT ''所属部门ID'' AFTER tenant_id', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 用户-角色回填：按旧 role 列映射（admin -> admin 角色，其余 -> common 角色，建角色后再回填）
-- 先建角色（见下方初始数据），再用关联表表达原角色关系

-- ============================================================
-- 初始数据
-- ============================================================

-- 默认租户的根部门
INSERT INTO sys_dept (id, tenant_id, parent_id, ancestors, dept_name, order_num, status)
SELECT 100, 1, 0, '0', '默认租户总部', 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE id = 100);

-- 角色：平台管理员（租户 0，全数据权限）/ 租户管理员 / 普通用户
INSERT INTO sys_role (id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
SELECT 1, 0, '平台管理员', 'admin', 1, 1, 1, '平台内置超级管理员（跨租户）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 1);

INSERT INTO sys_role (id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
SELECT 2, 1, '租户管理员', 'tenant_admin', 1, 4, 1, '默认租户管理员（本部门及以下数据）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 2);

INSERT INTO sys_role (id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
SELECT 3, 1, '普通用户', 'common', 2, 5, 1, '默认租户普通用户（仅本人数据）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 3);

-- 菜单：一级目录 + 二级菜单 + 按钮权限点
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 1, 0, '智能对话', 'M', 1, '/chat', 'message', NULL, 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 101, 1, '聊天', 'C', 1, 'index', 'chat', 'chat:use', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 101);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 2, 0, '系统管理', 'M', 2, '/system', 'setting', NULL, 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 201, 2, '用户管理', 'C', 1, 'user', 'user', 'system:user:list', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 201);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 202, 2, '角色管理', 'C', 2, 'role', 'peoples', 'system:role:list', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 202);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 203, 2, '菜单管理', 'C', 3, 'menu', 'tree-table', 'system:menu:list', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 203);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 204, 2, '部门管理', 'C', 4, 'dept', 'tree', 'system:dept:list', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 204);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 205, 2, '租户管理', 'C', 5, 'tenant', 'build', 'system:tenant:list', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 205);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 3, 0, 'Agent 配置', 'M', 3, '/agent', 'robot', NULL, 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 3);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 301, 3, '模型配置', 'C', 1, 'model', 'link', 'agent:model:list', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 301);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 302, 3, '运行参数', 'C', 2, 'agentconfig', 'slider', 'agent:config:list', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 302);

-- 用户管理按钮权限点
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2011, 201, '用户新增', 'F', 1, 'system:user:add', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2011);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2012, 201, '用户修改', 'F', 2, 'system:user:edit', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2012);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2013, 201, '用户删除', 'F', 3, 'system:user:remove', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2013);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, perms, visible, status)
SELECT 2014, 201, '重置密码', 'F', 4, 'system:user:resetPwd', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2014);

-- 平台管理员角色：授予全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 租户管理员：除租户管理外全部
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.id FROM sys_menu m
WHERE m.id <> 205
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id);

-- 普通用户：仅智能对话
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.id FROM sys_menu m
WHERE m.id IN (1, 101)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.id);

-- 存量用户回填部门与角色关联（旧 role 列：ADMIN -> 角色1，USER -> 角色3）
UPDATE sys_user SET dept_id = 100 WHERE dept_id IS NULL;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, CASE WHEN u.role = 'ADMIN' THEN 1 ELSE 3 END
FROM sys_user u
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id);

-- 删除旧 role 列（幂等：列存在才删）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role');
SET @ddl := IF(@col_exists > 0, 'ALTER TABLE sys_user DROP COLUMN role', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
