-- V51__dict_provider_and_log_labels.sql
-- 1. agent_model_provider 字典补全 anthropic / gemini
-- 2. 新增 oper_type / event_type / common_status 字典（日志页面下拉筛选取接口用）

-- ============================================================
-- 1. 补全模型提供商字典（anthropic、gemini）
-- ============================================================
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_model_provider', 'Anthropic Claude', 'anthropic', 7
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_model_provider' AND dict_value = 'anthropic');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_model_provider', 'Google Gemini', 'gemini', 8
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_model_provider' AND dict_value = 'gemini');

-- ============================================================
-- 2. 操作类型字典（sys_oper_log.oper_type 下拉）
-- ============================================================
INSERT INTO sys_dict_type (tenant_id, dict_name, dict_type, remark)
SELECT 0, '操作类型', 'oper_type', '业务操作日志的操作类型'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE tenant_id = 0 AND dict_type = 'oper_type');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'oper_type', '全部类型', 'all', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'oper_type' AND dict_value = 'all');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'oper_type', '新增', 'CREATE', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'oper_type' AND dict_value = 'CREATE');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'oper_type', '修改', 'UPDATE', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'oper_type' AND dict_value = 'UPDATE');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'oper_type', '删除', 'DELETE', 3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'oper_type' AND dict_value = 'DELETE');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'oper_type', '授权', 'GRANT', 4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'oper_type' AND dict_value = 'GRANT');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'oper_type', '其他', 'OTHER', 5
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'oper_type' AND dict_value = 'OTHER');

-- ============================================================
-- 3. 通用状态字典（成功/失败，日志页面状态筛选）
-- ============================================================
INSERT INTO sys_dict_type (tenant_id, dict_name, dict_type, remark)
SELECT 0, '通用状态', 'common_status', '通用成功/失败状态'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE tenant_id = 0 AND dict_type = 'common_status');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'common_status', '全部状态', 'all', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'common_status' AND dict_value = 'all');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'common_status', '成功', '1', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'common_status' AND dict_value = '1');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'common_status', '失败', '0', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'common_status' AND dict_value = '0');

-- ============================================================
-- 4. 事件类型字典（sys_login_log.event_type 下拉）
-- ============================================================
INSERT INTO sys_dict_type (tenant_id, dict_name, dict_type, remark)
SELECT 0, '事件类型', 'event_type', '登录登出事件类型'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE tenant_id = 0 AND dict_type = 'event_type');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'event_type', '全部事件', 'all', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'event_type' AND dict_value = 'all');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'event_type', '登录', 'LOGIN', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'event_type' AND dict_value = 'LOGIN');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'event_type', '登出', 'LOGOUT', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'event_type' AND dict_value = 'LOGOUT');
