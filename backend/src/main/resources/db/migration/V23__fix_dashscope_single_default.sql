-- ============================================================
-- V23__fix_dashscope_single_default.sql
-- Flyway 迁移脚本：订正 dashscope 双默认问题
--   V22 将 Qwen-Max 3.8 置为默认，但遗留了 Qwen-Plus 的旧默认标记，
--   同一厂商出现两个 is_default=1（下拉会展示两个「· 默认」）。
--   每厂商保持唯一默认：dashscope 以 Qwen-Max 3.8 为准。
-- 规约：迁移脚本只增不改；数据订正幂等（重复执行无副作用）
-- ============================================================

UPDATE sys_dict_data
SET is_default = 0
WHERE tenant_id = 0 AND dict_type = 'agent_provider_models' AND dict_value = 'dashscope@qwen-plus';
