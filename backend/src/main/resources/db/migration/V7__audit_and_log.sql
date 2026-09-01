-- ============================================================
-- V7__audit_and_log.sql
-- Flyway 迁移脚本：
--   1. 业务表补齐审计人员字段：creator / updater（时间字段已存在）
--      由 MyBatis Plus MetaObjectHandler 自动填充（创建人/修改人）
--   2. sys_oper_log   —— 业务操作日志（增删改/授权，成功失败均记录）
--   3. sys_login_log  —— 登录登出日志
--   4. 系统管理下新增「日志管理」菜单
-- 规约：迁移脚本只增不改，后续变更请新增 V8__xxx.sql
-- ============================================================

-- ------------------------------------------------------------
-- 1. 业务表补列：创建人 / 修改人
--    （关系表 sys_user_role / sys_role_menu / sys_role_dept 无审计字段，跳过）
-- ------------------------------------------------------------
ALTER TABLE sys_user              ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE sys_tenant            ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE sys_dept              ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE sys_role              ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE sys_menu              ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE sys_dict_type         ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE sys_dict_data         ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE agent_config          ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE model_provider_config ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE agent_preset          ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE prompt_template       ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE agent_pipeline        ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;
ALTER TABLE chat_session          ADD COLUMN creator VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER update_time,
                                  ADD COLUMN updater VARCHAR(64) DEFAULT NULL COMMENT '修改人' AFTER creator;

-- ------------------------------------------------------------
-- 2. 业务操作日志表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    tenant_id BIGINT       DEFAULT NULL            COMMENT '操作人所属租户ID',
    module    VARCHAR(64)  NOT NULL                COMMENT '功能模块（如 用户管理）',
    oper_type VARCHAR(16)  NOT NULL                COMMENT '操作类型：CREATE/UPDATE/DELETE/GRANT/OTHER',
    oper_desc VARCHAR(255) NOT NULL                COMMENT '操作描述',
    status    TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 成功 / 0 失败',
    error_msg VARCHAR(512) DEFAULT NULL            COMMENT '失败原因（成功时为空）',
    oper_name VARCHAR(64)  NOT NULL                COMMENT '操作人用户名',
    oper_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, oper_time),
    KEY idx_oper_name (oper_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业务操作日志表';

-- ------------------------------------------------------------
-- 3. 登录日志表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_login_log (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    username   VARCHAR(64) NOT NULL                COMMENT '用户名（登录失败时也记录）',
    tenant_id  BIGINT      DEFAULT NULL            COMMENT '用户所属租户ID（用户不存在时为空）',
    event_type VARCHAR(16) NOT NULL                COMMENT '事件类型：LOGIN / LOGOUT',
    status     TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：1 成功 / 0 失败',
    msg        VARCHAR(255) DEFAULT NULL           COMMENT '提示信息（失败原因等）',
    login_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, login_time),
    KEY idx_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录日志表';

-- ------------------------------------------------------------
-- 4. 日志管理菜单（系统管理下，权限 system:log:list）
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, order_num, path, icon, perms, visible, status)
SELECT 206, 2, '日志管理', 'C', 6, 'log', 'log', 'system:log:list', 1, 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 206);
