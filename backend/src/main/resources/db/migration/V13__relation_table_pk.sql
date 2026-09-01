-- ============================================================
-- V13__relation_table_pk.sql
-- Flyway 迁移脚本：RBAC 关联表补单列自增主键
--   背景：sys_user_role / sys_role_menu / sys_role_dept 为联合主键表，
--   MyBatis Plus 启动告警「Can not find table primary key」，xxById 方法不可用；
--   按规约（表必备 id 主键 + create_time）统一补单列自增主键。
--   方案：新增 id 自增列作主键，原关联对降级为唯一键（约束语义不变），
--   存量行由 ALTER 自动分配自增 id，不丢数据。
-- 规约：迁移脚本只增不改，后续变更请新增 V14__xxx.sql
-- ============================================================

-- ------------------------------------------------------------
-- 用户-角色关联表
-- ------------------------------------------------------------
ALTER TABLE sys_user_role
    DROP PRIMARY KEY,
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID' FIRST,
    ADD PRIMARY KEY (id),
    ADD UNIQUE KEY uk_user_role (user_id, role_id),
    ADD COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ------------------------------------------------------------
-- 角色-菜单关联表（权限点授权）
-- ------------------------------------------------------------
ALTER TABLE sys_role_menu
    DROP PRIMARY KEY,
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID' FIRST,
    ADD PRIMARY KEY (id),
    ADD UNIQUE KEY uk_role_menu (role_id, menu_id),
    ADD COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ------------------------------------------------------------
-- 角色-部门关联表（自定义数据权限）
-- ------------------------------------------------------------
ALTER TABLE sys_role_dept
    DROP PRIMARY KEY,
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID' FIRST,
    ADD PRIMARY KEY (id),
    ADD UNIQUE KEY uk_role_dept (role_id, dept_id),
    ADD COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
