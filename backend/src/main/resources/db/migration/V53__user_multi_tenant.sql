-- ============================================================
-- V53__user_multi_tenant.sql
-- Flyway 迁移脚本：用户多组织（多租户）兼容
--   1. sys_user_tenant —— 用户-组织关联表（合并原 sys_user_role 的角色维度）
--   2. 存量数据回填：从 sys_user + sys_user_role 建立初始关联
-- 设计思路：
--   将 sys_user_role 的角色分配合并到 sys_user_tenant，
--   每个 (user_id, tenant_id, role_id) 一行，角色按组织独立分配。
--   position（职位）放在 sys_user_tenant 上，每组织独立。
--   admin 平台管理员不属于任何组织，无 sys_user_tenant 记录，
--   通过 username='admin' 识别，拥有平台级全局权限。
--   userId 格式暂不变更（保持 {tenantCode}_{seq}），降低迁移风险。
-- 规约：迁移脚本只增不改，后续变更请新增 V54__xxx.sql
-- ============================================================

-- ------------------------------------------------------------
-- 1. 用户-组织关联表：合并成员资格 + 角色分配
-- 一个用户可属于多个组织，每个组织关系有独立的角色、部门和状态
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_tenant (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     VARCHAR(64)  NOT NULL                COMMENT '用户ID（关联 sys_user.id）',
    tenant_id   BIGINT       NOT NULL                COMMENT '租户ID（关联 sys_tenant.id）',
    role_id     BIGINT       NOT NULL                COMMENT '角色ID（关联 sys_role.id，该组织内的角色）',
    dept_id     BIGINT       DEFAULT NULL            COMMENT '该组织内的部门ID（关联 sys_dept.id）',
    position    VARCHAR(64)  DEFAULT NULL            COMMENT '该组织内的职位',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '该组织内的状态：1 启用 / 0 禁用',
    is_default  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认登录组织：1 是 / 0 否',
    creator     VARCHAR(64)  DEFAULT NULL            COMMENT '创建人用户名',
    creator_id  VARCHAR(64)  DEFAULT NULL            COMMENT '创建人ID',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updater     VARCHAR(64)  DEFAULT NULL            COMMENT '修改人用户名',
    updater_id  VARCHAR(64)  DEFAULT NULL            COMMENT '修改人ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_tenant_role (user_id, tenant_id, role_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-组织关联表（含角色分配，合并原 sys_user_role）';

-- ------------------------------------------------------------
-- 2. 存量数据回填：为每个现有用户建立默认组织关联
-- 从 sys_user 取 tenant_id，从 sys_user_role 取 role_id
-- 如果用户无角色记录，分配该租户的 common 角色（role_sort=2）
-- ------------------------------------------------------------
INSERT INTO sys_user_tenant (user_id, tenant_id, role_id, dept_id, status, is_default, creator, creator_id)
SELECT u.id, u.tenant_id,
       COALESCE(ur.role_id, (
           SELECT r.id FROM sys_role r
           WHERE r.tenant_id = u.tenant_id AND r.role_key = 'common'
           LIMIT 1
       )),
       u.dept_id, 1, 1, 'system', NULL
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id = u.id
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_tenant ut
    WHERE ut.user_id = u.id AND ut.tenant_id = u.tenant_id
);
