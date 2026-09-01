-- ============================================================
-- V26：作用域 GLOBAL → PLATFORM 统一
-- 后端 ConfigService 使用 "PLATFORM" 作为平台级作用域值，
-- 但历史迁移脚本（V3/V9/V18）写入的是 "GLOBAL"，导致后端
-- 按 scope='PLATFORM' 查询时找不到平台级默认配置。
-- 本脚本将存量数据与表默认值统一为 "PLATFORM"。
-- ============================================================

-- 1. 更新存量数据
UPDATE model_provider_config SET scope = 'PLATFORM' WHERE scope = 'GLOBAL';
UPDATE agent_config          SET scope = 'PLATFORM' WHERE scope = 'GLOBAL';
UPDATE tool_config           SET scope = 'PLATFORM' WHERE scope = 'GLOBAL';
UPDATE mcp_server            SET scope = 'PLATFORM' WHERE scope = 'GLOBAL';

-- 2. 修改表列默认值（后续 INSERT 不再依赖 GLOBAL）
ALTER TABLE model_provider_config MODIFY COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' COMMENT '作用域：PLATFORM / TENANT / USER';
ALTER TABLE agent_config          MODIFY COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' COMMENT '作用域：PLATFORM / TENANT / USER';
ALTER TABLE tool_config           MODIFY COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' COMMENT '作用域：PLATFORM / TENANT / USER';
ALTER TABLE mcp_server            MODIFY COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' COMMENT '作用域：PLATFORM / TENANT / USER';
