-- ------------------------------------------------------------
-- V18：Agent 能力配置（MCP 服务器 / 工具开关 / 技能开关）
-- 三类能力此前或硬编码（工具）、或仅运行时自学习（技能）、或未接入（MCP），
-- 平台无可视化配置入口。本脚本将其数据库化，复用既有三级作用域
-- （USER > TENANT > GLOBAL 就近覆盖）与配置变更热重建机制。
-- 敏感字段（MCP headers / env）AES 加密存储，与 model_provider_config.api_key 一致。
-- ------------------------------------------------------------

-- MCP 服务器登记：Agent 构建时挂载其暴露的工具
CREATE TABLE IF NOT EXISTS mcp_server (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    scope       VARCHAR(16)   NOT NULL                COMMENT '作用域：GLOBAL / TENANT / USER',
    tenant_id   BIGINT        NOT NULL                COMMENT '租户ID（GLOBAL 为 0）',
    owner_name  VARCHAR(64)   DEFAULT NULL            COMMENT '用户级配置归属用户名（非 USER 作用域为 NULL）',
    name        VARCHAR(64)   NOT NULL                COMMENT 'MCP 服务器唯一名（同作用域内唯一，作为工具命名空间）',
    transport   VARCHAR(24)   NOT NULL                COMMENT '传输方式：stdio / sse / http / streamable-http',
    command     VARCHAR(255)  DEFAULT NULL            COMMENT 'stdio 启动命令（transport=stdio 时必填）',
    args        VARCHAR(1024) DEFAULT NULL            COMMENT 'stdio 启动参数 JSON 数组',
    url         VARCHAR(512)  DEFAULT NULL            COMMENT '服务端点（sse / http / streamable-http 时必填）',
    headers     VARCHAR(2048) DEFAULT NULL            COMMENT 'HTTP 请求头 JSON（AES 加密存储，可含鉴权令牌）',
    env         VARCHAR(2048) DEFAULT NULL            COMMENT 'stdio 环境变量 JSON（AES 加密存储，可含密钥）',
    enable_tools VARCHAR(1024) DEFAULT NULL           COMMENT '仅启用的工具名 JSON 数组（留空=全部启用）',
    enabled     TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 禁用',
    remark      VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator     VARCHAR(64)   DEFAULT NULL            COMMENT '创建人用户名',
    updater     VARCHAR(64)   DEFAULT NULL            COMMENT '修改人用户名',
    creator_id  BIGINT        DEFAULT NULL            COMMENT '创建人用户ID',
    updater_id  BIGINT        DEFAULT NULL            COMMENT '修改人用户ID',
    PRIMARY KEY (id),
    KEY idx_mcp_scope (scope, tenant_id, owner_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MCP 服务器登记表（三级作用域）';

-- 工具开关：控制内置/自定义工具的启用（缺省视为启用）
CREATE TABLE IF NOT EXISTS tool_config (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    scope       VARCHAR(16)   NOT NULL                COMMENT '作用域：GLOBAL / TENANT / USER',
    tenant_id   BIGINT        NOT NULL                COMMENT '租户ID（GLOBAL 为 0）',
    owner_name  VARCHAR(64)   DEFAULT NULL            COMMENT '用户级配置归属用户名（非 USER 作用域为 NULL）',
    tool_key    VARCHAR(64)   NOT NULL                COMMENT '工具键：内置/自定义工具的固定标识',
    enabled     TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 禁用',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator     VARCHAR(64)   DEFAULT NULL            COMMENT '创建人用户名',
    updater     VARCHAR(64)   DEFAULT NULL            COMMENT '修改人用户名',
    creator_id  BIGINT        DEFAULT NULL            COMMENT '创建人用户ID',
    updater_id  BIGINT        DEFAULT NULL            COMMENT '修改人用户ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_scope_key (scope, tenant_id, owner_name, tool_key),
    KEY idx_tool_scope (scope, tenant_id, owner_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工具开关配置表（三级作用域）';

-- 技能开关：控制工作区技能的启用（缺省视为启用，按技能名就近覆盖）
CREATE TABLE IF NOT EXISTS skill_config (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    scope       VARCHAR(16)   NOT NULL                COMMENT '作用域：GLOBAL / TENANT / USER',
    tenant_id   BIGINT        NOT NULL                COMMENT '租户ID（GLOBAL 为 0）',
    owner_name  VARCHAR(64)   DEFAULT NULL            COMMENT '用户级配置归属用户名（非 USER 作用域为 NULL）',
    skill_name  VARCHAR(64)   NOT NULL                COMMENT '技能名（对应工作区 skills/<name>/SKILL.md）',
    enabled     TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 禁用',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator     VARCHAR(64)   DEFAULT NULL            COMMENT '创建人用户名',
    updater     VARCHAR(64)   DEFAULT NULL            COMMENT '修改人用户名',
    creator_id  BIGINT        DEFAULT NULL            COMMENT '创建人用户ID',
    updater_id  BIGINT        DEFAULT NULL            COMMENT '修改人用户ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_scope_name (scope, tenant_id, owner_name, skill_name),
    KEY idx_skill_scope (scope, tenant_id, owner_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '技能开关配置表（三级作用域）';
