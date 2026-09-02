-- V52__add_glm52_model.sql
-- 火山方舟 GLM-5.2 模型接入：
--   1. 字典 agent_provider_models 增加 glm-5-2-260617
-- 规约：脚本只增不改；幂等插入（NOT EXISTS 防重）

-- ------------------------------------------------------------
-- 1. 候选模型目录：volcengine@glm-5-2-260617
--    GLM-5.2：面向长程任务设计的旗舰模型，1M 上下文，支持深度思考
-- ------------------------------------------------------------
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, remark)
SELECT 0, 'agent_provider_models', 'GLM-5.2 (1M)', 'volcengine@glm-5-2-260617', 25, 'volcengine'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'volcengine@glm-5-2-260617');
