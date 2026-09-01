-- ============================================================
-- V3__multi_tenant_config.sql
-- Flyway 迁移脚本：多租户架构 + 配置落库
--   1. sys_tenant               —— 租户表
--   2. sys_user 增加 tenant_id  —— 用户归属租户
--   3. model_provider_config    —— 模型提供商配置（GLOBAL/TENANT/USER 三级，就近覆盖）
--   4. agent_config             —— Agent 运行参数（KV，三级，就近覆盖）
-- 配置解析优先级：USER > TENANT > GLOBAL；AgentScope 本身再按 (userId, sessionId) 隔离状态
-- 规约：迁移脚本只增不改，后续变更请新增 V4__xxx.sql
-- ============================================================

-- ------------------------------------------------------------
-- 租户表：多租户隔离的最顶层组织单元
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_tenant (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '租户ID',
    tenant_code VARCHAR(64)  NOT NULL                COMMENT '租户编码（唯一，英文标识）',
    tenant_name VARCHAR(128) NOT NULL                COMMENT '租户名称',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 禁用',
    remark      VARCHAR(255) DEFAULT NULL            COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户表';

-- 默认租户：存量/新注册用户未指定租户时归属此租户
INSERT INTO sys_tenant (tenant_code, tenant_name, status, remark)
SELECT 'default', '默认租户', 1, '系统内置默认租户'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE tenant_code = 'default');

-- 用户表挂租户（幂等：MySQL 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 条件执行）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''所属租户ID（关联 sys_tenant.id）'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE sys_user ADD INDEX idx_tenant (tenant_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 模型提供商配置表：三级作用域
--   scope=GLOBAL：全局默认（tenant_id=0, owner_name 空）
--   scope=TENANT：租户级覆盖（tenant_id=租户ID）
--   scope=USER  ：用户级覆盖（owner_name=用户名）
-- 同一作用域内 provider 唯一；is_current 标记该作用域内当前生效提供商
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS model_provider_config (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    scope        VARCHAR(16)   NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域：GLOBAL / TENANT / USER',
    tenant_id    BIGINT        NOT NULL DEFAULT 0      COMMENT '租户ID（GLOBAL 为 0）',
    owner_name   VARCHAR(64)   DEFAULT NULL            COMMENT '用户级配置的归属用户名',
    provider     VARCHAR(32)   NOT NULL                COMMENT '提供商标识：openai / dashscope / ollama',
    display_name VARCHAR(64)   NOT NULL                COMMENT '展示名称',
    enabled      TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 禁用',
    is_current   TINYINT       NOT NULL DEFAULT 0      COMMENT '是否该作用域内当前生效提供商',
    api_key      VARCHAR(512)  DEFAULT NULL            COMMENT 'API Key（AES 加密存储，前缀 enc:）',
    base_url     VARCHAR(256)  DEFAULT NULL            COMMENT '自定义端点；openai 兼容协议可指向 DeepSeek/Kimi/vLLM',
    model_name   VARCHAR(128)  NOT NULL                COMMENT '模型标识，如 qwen-plus / gpt-4.1-mini',
    extra_config VARCHAR(1024) DEFAULT NULL            COMMENT '扩展参数（JSON），如 {"temperature":0.7}',
    remark       VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_provider (scope, tenant_id, owner_name, provider)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '模型提供商配置表（三级作用域）';

-- ------------------------------------------------------------
-- Agent 运行参数表：KV + 三级作用域
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    scope        VARCHAR(16)  NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域：GLOBAL / TENANT / USER',
    tenant_id    BIGINT       NOT NULL DEFAULT 0      COMMENT '租户ID（GLOBAL 为 0）',
    owner_name   VARCHAR(64)  DEFAULT NULL            COMMENT '用户级配置的归属用户名',
    config_key   VARCHAR(64)  NOT NULL                COMMENT '配置键，如 state_store_type / permission_mode',
    config_value VARCHAR(512) NOT NULL                COMMENT '配置值',
    remark       VARCHAR(255) DEFAULT NULL            COMMENT '配置说明',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_key (scope, tenant_id, owner_name, config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 运行参数表（三级作用域）';

-- ------------------------------------------------------------
-- 初始数据：全局（GLOBAL）模型提供商默认配置
-- ------------------------------------------------------------
INSERT INTO model_provider_config (scope, tenant_id, provider, display_name, enabled, is_current, model_name, remark)
SELECT 'GLOBAL', 0, 'dashscope', '阿里云通义千问', 1, 1, 'qwen-plus', '全局默认提供商；租户/用户可覆盖或自带 API Key'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM model_provider_config WHERE scope = 'GLOBAL' AND provider = 'dashscope');

INSERT INTO model_provider_config (scope, tenant_id, provider, display_name, enabled, is_current, base_url, model_name, remark)
SELECT 'GLOBAL', 0, 'openai', 'OpenAI 及兼容协议', 1, 0, '', 'gpt-4.1-mini', 'base_url 留空走官方；兼容 DeepSeek/Kimi/vLLM'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM model_provider_config WHERE scope = 'GLOBAL' AND provider = 'openai');

INSERT INTO model_provider_config (scope, tenant_id, provider, display_name, enabled, is_current, base_url, model_name, remark)
SELECT 'GLOBAL', 0, 'ollama', '本地 Ollama', 1, 0, 'http://localhost:11434', 'qwen2.5:7b', '本地模型，免 API Key'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM model_provider_config WHERE scope = 'GLOBAL' AND provider = 'ollama');

-- ------------------------------------------------------------
-- 初始数据：全局（GLOBAL）Agent 运行参数默认值
-- ------------------------------------------------------------
INSERT INTO agent_config (scope, tenant_id, config_key, config_value, remark)
SELECT 'GLOBAL', 0, 'state_store_type', 'redis', 'Agent 会话状态存储：redis（分布式）/ json（本地文件）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM agent_config WHERE scope = 'GLOBAL' AND config_key = 'state_store_type');

INSERT INTO agent_config (scope, tenant_id, config_key, config_value, remark)
SELECT 'GLOBAL', 0, 'permission_mode', 'DEFAULT', '权限模式：DEFAULT/ACCEPT_EDITS/EXPLORE/BYPASS/DONT_ASK'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM agent_config WHERE scope = 'GLOBAL' AND config_key = 'permission_mode');

INSERT INTO agent_config (scope, tenant_id, config_key, config_value, remark)
SELECT 'GLOBAL', 0, 'compaction_trigger_messages', '30', '上下文压缩触发阈值（消息条数）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM agent_config WHERE scope = 'GLOBAL' AND config_key = 'compaction_trigger_messages');

INSERT INTO agent_config (scope, tenant_id, config_key, config_value, remark)
SELECT 'GLOBAL', 0, 'compaction_keep_messages', '10', '压缩时保留的最近消息条数'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM agent_config WHERE scope = 'GLOBAL' AND config_key = 'compaction_keep_messages');

INSERT INTO agent_config (scope, tenant_id, config_key, config_value, remark)
SELECT 'GLOBAL', 0, 'memory_flush_throttle_minutes', '10', '长期记忆 flush 节流间隔（分钟）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM agent_config WHERE scope = 'GLOBAL' AND config_key = 'memory_flush_throttle_minutes');

-- ------------------------------------------------------------
-- chat_session 挂租户（便于按租户审计会话；同样用条件执行保证幂等）
-- ------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_session' AND COLUMN_NAME = 'tenant_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE chat_session ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''所属租户ID'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
