-- ------------------------------------------------------------
-- V60__token_usage_turn_id.sql
-- Token 流水表增加回合ID字段
--
-- 功能:
-- 同一次用户消息触发的所有模型调用共享同一个 turn_id，
-- 前端可按 turn 聚合展示"这条消息总共花了多少 Token"。
-- ------------------------------------------------------------

-- 新增 turn_id 列（VARCHAR(36) 存放 UUID）
ALTER TABLE token_usage_log
    ADD COLUMN turn_id VARCHAR(36) COMMENT '回合ID：同一次用户消息触发的所有模型调用共享此ID';

-- 按 turn_id 查询的索引（前端按回合聚合时高频使用）
CREATE INDEX idx_token_usage_log_turn_id ON token_usage_log (turn_id);
