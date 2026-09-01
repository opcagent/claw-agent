-- ------------------------------------------------------------
-- V44：定时任务 / 定时 Agent
-- 用户可配置定时任务，按 Cron 表达式自动触发 Agent 对话，
-- 执行结果可选邮件通知。适用于「每日早报」「周报汇总」等自动化场景。
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS scheduled_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id       BIGINT       NOT NULL                COMMENT '所属租户ID',
    user_id         VARCHAR(64)  NOT NULL                COMMENT '所属用户ID',
    username        VARCHAR(64)  NOT NULL                COMMENT '所属用户名（冗余）',
    task_name       VARCHAR(128) NOT NULL                COMMENT '任务名称',
    cron_expr       VARCHAR(64)  NOT NULL                COMMENT 'Cron 表达式',
    preset_code     VARCHAR(64)  DEFAULT NULL            COMMENT '预设模板编码（可选）',
    pipeline_code   VARCHAR(64)  DEFAULT NULL            COMMENT '流水线编码（可选）',
    prompt_content  TEXT         NOT NULL                COMMENT '发送给 Agent 的消息内容',
    notify_email    VARCHAR(256) DEFAULT NULL            COMMENT '结果通知邮箱（为空不通知）',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 禁用',
    last_run_time   DATETIME     DEFAULT NULL            COMMENT '上次执行时间',
    next_run_time   DATETIME     DEFAULT NULL            COMMENT '下次执行时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator         VARCHAR(64)  DEFAULT NULL            COMMENT '创建人',
    updater         VARCHAR(64)  DEFAULT NULL            COMMENT '修改人',
    creator_id      VARCHAR(64)  DEFAULT NULL            COMMENT '创建人ID',
    updater_id      VARCHAR(64)  DEFAULT NULL            COMMENT '修改人ID',
    PRIMARY KEY (id),
    KEY idx_user_enabled (user_id, enabled),
    KEY idx_next_run (enabled, next_run_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '定时任务表';

CREATE TABLE IF NOT EXISTS scheduled_task_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT       NOT NULL                COMMENT '关联任务ID',
    tenant_id   BIGINT       NOT NULL                COMMENT '所属租户ID',
    status      VARCHAR(16)  NOT NULL                COMMENT '执行状态：SUCCESS / FAIL',
    result_text MEDIUMTEXT   DEFAULT NULL            COMMENT '执行结果摘要',
    run_time    DATETIME     NOT NULL                COMMENT '执行时间',
    error_msg   VARCHAR(512) DEFAULT NULL            COMMENT '错误信息（失败时）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator     VARCHAR(64)  DEFAULT NULL            COMMENT '创建人',
    updater     VARCHAR(64)  DEFAULT NULL            COMMENT '修改人',
    creator_id  VARCHAR(64)  DEFAULT NULL            COMMENT '创建人ID',
    updater_id  VARCHAR(64)  DEFAULT NULL            COMMENT '修改人ID',
    PRIMARY KEY (id),
    KEY idx_task (task_id, run_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '定时任务执行日志';

-- 定时任务菜单（挂在「AI 工作台」下）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, icon, menu_type, visible, status, perms)
SELECT '定时任务', id, 10, '/scheduled-tasks', 'time-range', 'C', 1, 1, 'schedule:task:list'
FROM sys_menu
WHERE menu_name = 'AI 工作台' AND menu_type = 'M'
LIMIT 1;

-- 角色授权：三个内置角色均可访问定时任务菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r, sys_menu m
WHERE m.menu_name = '定时任务' AND m.menu_type = 'C' AND m.path = '/scheduled-tasks'
  AND r.id IN (1, 2, 3)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
