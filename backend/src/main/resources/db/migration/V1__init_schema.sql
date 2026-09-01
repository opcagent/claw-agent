-- ============================================================
-- V1__init_schema.sql
-- Flyway 迁移脚本：初始化基础表结构
-- 规约：迁移脚本只增不改，后续变更请新增 V2__xxx.sql
-- ============================================================

-- ------------------------------------------------------------
-- 用户表：存储登录账号与角色（RBAC）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID（主键）',
    username    VARCHAR(64)  NOT NULL                COMMENT '登录用户名（唯一，同时作为 Agent 的 userId 隔离键）',
    password    VARCHAR(128) NOT NULL                COMMENT 'BCrypt 加密后的密码',
    nickname    VARCHAR(64)  DEFAULT NULL            COMMENT '昵称（展示用）',
    role        VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER 普通用户 / ADMIN 管理员',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 禁用',
    remark      VARCHAR(255) DEFAULT NULL            COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户表';

-- ------------------------------------------------------------
-- 会话记录表：记录用户与 Agent 的会话元数据，
-- 便于前端展示历史会话列表；对话内容本身由 AgentScope 会话日志维护
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id  VARCHAR(64)  NOT NULL                COMMENT 'AgentScope sessionId',
    username    VARCHAR(64)  NOT NULL                COMMENT '所属用户',
    title       VARCHAR(128) DEFAULT NULL            COMMENT '会话标题（首条消息摘要）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_session (username, session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天会话元数据表';
