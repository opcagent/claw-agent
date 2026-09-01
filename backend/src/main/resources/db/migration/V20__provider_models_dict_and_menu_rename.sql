-- ============================================================
-- V20__provider_models_dict_and_menu_rename.sql
-- Flyway 迁移脚本：
--   1. 新增字典「模型厂商模型目录」agent_provider_models：
--      dict_value = 厂商@模型名（首个 @ 分隔，模型名自身可含冒号），
--      is_default 标记该厂商默认模型；前端按厂商过滤生成模型下拉，
--      字典管理页可直接维护，无需改代码。
--   2. 菜单名称升级（产品级命名，增强平台质感；路径/权限点不变，授权不受影响）
-- 规约：迁移脚本只增不改；脚本内部保持幂等（NOT EXISTS 防重）
-- ============================================================

-- ------------------------------------------------------------
-- 1. 字典类型：模型厂商模型目录
-- ------------------------------------------------------------
INSERT INTO sys_dict_type (tenant_id, dict_name, dict_type, remark)
SELECT 0, '模型厂商模型目录', 'agent_provider_models', '按厂商预置的候选模型；dict_value 格式：厂商@模型名'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE tenant_id = 0 AND dict_type = 'agent_provider_models');

-- 阿里云通义千问
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, is_default, remark)
SELECT 0, 'agent_provider_models', 'Qwen-Plus', 'dashscope@qwen-plus', 1, 1, 'dashscope'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-plus');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'Qwen-Turbo', 'dashscope@qwen-turbo', 2, 'dashscope'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-turbo');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'Qwen-Max', 'dashscope@qwen-max', 3, 'dashscope'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-max');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'Qwen-Long', 'dashscope@qwen-long', 4, 'dashscope'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-long');

-- DeepSeek
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, is_default, remark)
SELECT 0, 'agent_provider_models', 'DeepSeek-Chat', 'deepseek@deepseek-chat', 5, 1, 'deepseek'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'deepseek@deepseek-chat');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'DeepSeek-Reasoner', 'deepseek@deepseek-reasoner', 6, 'deepseek'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'deepseek@deepseek-reasoner');

-- OpenAI 及兼容协议
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, is_default, remark)
SELECT 0, 'agent_provider_models', 'GPT-5', 'openai@gpt-5', 7, 1, 'openai'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'openai@gpt-5');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'GPT-5-mini', 'openai@gpt-5-mini', 8, 'openai'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'openai@gpt-5-mini');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'GPT-4.1', 'openai@gpt-4.1', 9, 'openai'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'openai@gpt-4.1');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'GPT-4.1-mini', 'openai@gpt-4.1-mini', 10, 'openai'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'openai@gpt-4.1-mini');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'GPT-4o', 'openai@gpt-4o', 11, 'openai'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'openai@gpt-4o');

-- 本地 Ollama（模型名自带冒号，故厂商与模型用 @ 分隔）
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, is_default, remark)
SELECT 0, 'agent_provider_models', 'Qwen2.5 7B（本地）', 'ollama@qwen2.5:7b', 12, 1, 'ollama'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'ollama@qwen2.5:7b');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'Llama3.1 8B（本地）', 'ollama@llama3.1:8b', 13, 'ollama'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'ollama@llama3.1:8b');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'DeepSeek-R1 7B（本地）', 'ollama@deepseek-r1:7b', 14, 'ollama'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'ollama@deepseek-r1:7b');

-- ------------------------------------------------------------
-- 2. 菜单名称升级（只改 menu_name，路径/图标/权限点保持不变）
-- ------------------------------------------------------------
-- 一级目录
UPDATE sys_menu SET menu_name = 'AI 工作台'   WHERE id = 1;
UPDATE sys_menu SET menu_name = '人格预设'     WHERE id = 102;
UPDATE sys_menu SET menu_name = '自动化流水线' WHERE id = 4;
UPDATE sys_menu SET menu_name = '平台治理'     WHERE id = 2;
UPDATE sys_menu SET menu_name = '智能体引擎'   WHERE id = 3;

-- 二级菜单
UPDATE sys_menu SET menu_name = '智能对话'   WHERE id = 101;
UPDATE sys_menu SET menu_name = '成员与账户' WHERE id = 201;
UPDATE sys_menu SET menu_name = '角色与权限' WHERE id = 202;
UPDATE sys_menu SET menu_name = '菜单权限'   WHERE id = 203;
UPDATE sys_menu SET menu_name = '组织架构'   WHERE id = 204;
UPDATE sys_menu SET menu_name = '租户空间'   WHERE id = 205;
UPDATE sys_menu SET menu_name = '审计日志'   WHERE id = 206;
UPDATE sys_menu SET menu_name = '数据字典'   WHERE id = 207;
UPDATE sys_menu SET menu_name = '模型与能力' WHERE id = 301;
