-- ============================================================
-- V47：接入火山方舟（豆包大模型）提供商
--   1. 字典 agent_model_provider 增加 volcengine 选项（前端下拉）
--   2. 字典 agent_provider_models 增加火山方舟候选模型
--   3. model_provider_config 增加 GLOBAL 级火山方舟种子配置
-- 火山方舟完全兼容 OpenAI Chat Completions 协议，
-- 后端复用 OpenAIChatModel + OpenAIChatFormatter，仅 base-url 不同。
-- 规约：脚本只增不改；幂等插入（NOT EXISTS 防重）
-- ============================================================

-- ------------------------------------------------------------
-- 1. 提供商下拉字典：volcengine
-- ------------------------------------------------------------
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_model_provider', '火山方舟（豆包）', 'volcengine', 6
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_model_provider' AND dict_value = 'volcengine');

-- ------------------------------------------------------------
-- 2. 候选模型目录：volcengine@模型名
--    默认模型标记 is_default=1（Seed 2.1 Pro）
-- ------------------------------------------------------------
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, is_default, remark)
SELECT 0, 'agent_provider_models', 'Seed 2.1 Pro', 'volcengine@doubao-seed-2-1-pro-260628', 20, 1, 'volcengine'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'volcengine@doubao-seed-2-1-pro-260628');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'Seed 2.1 Lite', 'volcengine@doubao-seed-2-1-lite-260628', 21, 'volcengine'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'volcengine@doubao-seed-2-1-lite-260628');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'Doubao Pro 128K', 'volcengine@doubao-pro-128k', 22, 'volcengine'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'volcengine@doubao-pro-128k');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'Doubao Pro 32K', 'volcengine@doubao-pro-32k', 23, 'volcengine'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'volcengine@doubao-pro-32k');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'Doubao Lite 32K', 'volcengine@doubao-lite-32k', 24, 'volcengine'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'volcengine@doubao-lite-32k');

-- ------------------------------------------------------------
-- 3. GLOBAL 级种子配置：is_current=0 不抢占现有生效提供商，需用户手动切换
--    base_url 默认北京区域通用端点；model_name 默认 Seed 2.1 Pro
-- ------------------------------------------------------------
INSERT INTO model_provider_config (scope, tenant_id, provider, display_name, enabled, is_current, base_url, model_name, remark)
SELECT 'GLOBAL', 0, 'volcengine', '火山方舟（豆包）', 1, 0,
       'https://ark.cn-beijing.volces.com/api/v3',
       'doubao-seed-2-1-pro-260628',
       'OpenAI 兼容协议；model 填 Endpoint ID 或模型名；API Key 环境变量 ARK_API_KEY 兜底'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM model_provider_config WHERE scope = 'GLOBAL' AND provider = 'volcengine');
