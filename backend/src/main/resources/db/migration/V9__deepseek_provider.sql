-- ============================================================
-- Flyway 迁移脚本：接入 DeepSeek 提供商
--   1. 字典 agent_model_provider 增加 deepseek 选项（前端下拉）
--   2. model_provider_config 增加 GLOBAL 级 DeepSeek 种子配置
-- 规约：脚本只增不改；幂等插入（NOT EXISTS 防重）
-- ============================================================

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_model_provider', 'DeepSeek', 'deepseek', 4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_model_provider' AND dict_value = 'deepseek');

-- DeepSeek 走 OpenAI 兼容协议：base_url 默认官方端点，模型默认 deepseek-chat；
-- is_current=0 不抢占现有生效提供商，需用户手动切换
INSERT INTO model_provider_config (scope, tenant_id, provider, display_name, enabled, is_current, base_url, model_name, remark)
SELECT 'GLOBAL', 0, 'deepseek', 'DeepSeek', 1, 0, 'https://api.deepseek.com', 'deepseek-chat', 'OpenAI 兼容协议；API Key 支持环境变量 DEEPSEEK_API_KEY 兜底'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM model_provider_config WHERE scope = 'GLOBAL' AND provider = 'deepseek');
