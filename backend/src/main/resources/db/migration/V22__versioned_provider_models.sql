-- ============================================================
-- V22__versioned_provider_models.sql
-- Flyway 迁移脚本：模型厂商模型目录升级为「带版本号」的模型
--   1. Qwen-Max 拆分为 Qwen-Max 3.8（默认）/ Qwen-Max 3.7
--   2. DeepSeek 由 Chat/Reasoner 换为 V4-Pro（默认）/ V4-Flash
--   3. 全量重排 dict_sort 保持下拉顺序整洁
-- 说明：仅改字典目录（下拉候选），已保存的提供商配置存的是模型名字符串，
--       不受影响；若引用了被替换的旧模型名，字典管理页可手工维护回。
-- 规约：迁移脚本只增不改；数据订正保持幂等（按旧值定位 / NOT EXISTS 防重）
-- ============================================================

-- ------------------------------------------------------------
-- 1. Qwen-Max → Qwen-Max 3.8（默认）：原地升级旧行
-- ------------------------------------------------------------
UPDATE sys_dict_data
SET dict_label = 'Qwen-Max 3.8',
    dict_value = 'dashscope@qwen-max-3.8',
    is_default = 1
WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-max';

-- Qwen-Max 3.7：新增（幂等防重）
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'Qwen-Max 3.7', 'dashscope@qwen-max-3.7', 2, 'dashscope'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-max-3.7');

-- ------------------------------------------------------------
-- 2. DeepSeek：Chat → V4-Pro（默认）、Reasoner → V4-Flash
-- ------------------------------------------------------------
UPDATE sys_dict_data
SET dict_label = 'DeepSeek-V4-Pro',
    dict_value = 'deepseek@deepseek-v4-pro',
    is_default = 1
WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'deepseek@deepseek-chat';

UPDATE sys_dict_data
SET dict_label = 'DeepSeek-V4-Flash',
    dict_value = 'deepseek@deepseek-v4-flash'
WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'deepseek@deepseek-reasoner';

-- ------------------------------------------------------------
-- 3. 全量重排：dashscope(1-5) → deepseek(6-7) → openai(8-12) → ollama(13-15)
-- ------------------------------------------------------------
UPDATE sys_dict_data SET dict_sort = 1  WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-max-3.8';
UPDATE sys_dict_data SET dict_sort = 2  WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-max-3.7';
UPDATE sys_dict_data SET dict_sort = 3  WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-plus';
UPDATE sys_dict_data SET dict_sort = 4  WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-turbo';
UPDATE sys_dict_data SET dict_sort = 5  WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-long';
UPDATE sys_dict_data SET dict_sort = 6  WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'deepseek@deepseek-v4-pro';
UPDATE sys_dict_data SET dict_sort = 7  WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'deepseek@deepseek-v4-flash';
UPDATE sys_dict_data SET dict_sort = 8  WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'openai@gpt-5';
UPDATE sys_dict_data SET dict_sort = 9  WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'openai@gpt-5-mini';
UPDATE sys_dict_data SET dict_sort = 10 WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'openai@gpt-4.1';
UPDATE sys_dict_data SET dict_sort = 11 WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'openai@gpt-4.1-mini';
UPDATE sys_dict_data SET dict_sort = 12 WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'openai@gpt-4o';
UPDATE sys_dict_data SET dict_sort = 13 WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'ollama@qwen2.5:7b';
UPDATE sys_dict_data SET dict_sort = 14 WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'ollama@llama3.1:8b';
UPDATE sys_dict_data SET dict_sort = 15 WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'ollama@deepseek-r1:7b';
