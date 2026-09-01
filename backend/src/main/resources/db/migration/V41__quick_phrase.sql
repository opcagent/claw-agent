-- ------------------------------------------------------------
-- V41：常用语/快捷指令表
-- 用户可预存常用 prompt，聊天时通过「/」快捷面板一键填入，
-- 减少重复输入，提升对话效率。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_quick_phrase (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL                COMMENT '所属租户ID',
    user_id     VARCHAR(64)  NOT NULL                COMMENT '所属用户ID',
    username    VARCHAR(64)  NOT NULL                COMMENT '所属用户名（冗余，便于审计）',
    title       VARCHAR(64)  NOT NULL                COMMENT '快捷指令标题',
    content     TEXT         NOT NULL                COMMENT '发送内容',
    sort_order  INT          NOT NULL DEFAULT 0      COMMENT '排序序号（越小越靠前）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator     VARCHAR(64)  DEFAULT NULL            COMMENT '创建人',
    updater     VARCHAR(64)  DEFAULT NULL            COMMENT '修改人',
    creator_id  VARCHAR(64)  DEFAULT NULL            COMMENT '创建人ID',
    updater_id  VARCHAR(64)  DEFAULT NULL            COMMENT '修改人ID',
    PRIMARY KEY (id),
    KEY idx_user (user_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户常用语/快捷指令表';
