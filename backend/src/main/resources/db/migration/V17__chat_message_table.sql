-- ------------------------------------------------------------
-- V17：聊天记录入库
-- 此前对话内容仅由 AgentScope 会话日志（文件系统）维护，
-- 前端打开历史会话无法回看消息；本表将用户消息与助手回复
-- 逐条落库，支持按会话回看与审计追溯。
-- 会话元数据仍归 chat_session，两者职责分离。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT        NOT NULL                COMMENT '所属租户ID（按租户审计）',
    session_id  VARCHAR(64)   NOT NULL                COMMENT 'AgentScope sessionId（关联 chat_session.session_id）',
    username    VARCHAR(64)   NOT NULL                COMMENT '所属用户（对应 sys_user.username）',
    role        VARCHAR(16)   NOT NULL                COMMENT '消息角色：user / assistant',
    content     MEDIUMTEXT    DEFAULT NULL            COMMENT '消息文本内容',
    attachments VARCHAR(2048) DEFAULT NULL            COMMENT '附件文件名 JSON 数组（仅用户消息）',
    status      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 正常 / 0 失败（助手回复执行异常）',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator     VARCHAR(64)   DEFAULT NULL            COMMENT '创建人用户名',
    updater     VARCHAR(64)   DEFAULT NULL            COMMENT '修改人用户名',
    creator_id  BIGINT        DEFAULT NULL            COMMENT '创建人用户ID',
    updater_id  BIGINT        DEFAULT NULL            COMMENT '修改人用户ID',
    PRIMARY KEY (id),
    KEY idx_session (session_id),
    KEY idx_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天消息记录表（对话内容入库）';
