-- ============================================================
-- V58__token_optimization.sql
-- Token 使用优化：配额管理 + 流水清理索引
--
-- 功能：
-- 1. token_usage_log 增加 usage_time 索引（定期清理旧数据用）
-- 2. 插入默认 Token 月度配额配置（agent_config，三级作用域）
--    0 = 不限制；>0 = 月度上限（单位：万 tokens）
-- ============================================================

-- ------------------------------------------------------------
-- 1. 为 token_usage_log 添加 usage_time 索引（清理旧流水用）
--    V32 的 idx_user_date 引用的 usage_date 列实际不存在，此处补建正确索引
--    MySQL 不支持 CREATE INDEX IF NOT EXISTS，用存储过程保证幂等
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_token_log_time_index;

DELIMITER $$

CREATE PROCEDURE add_token_log_time_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'token_usage_log'
          AND INDEX_NAME = 'idx_token_log_usage_time'
    ) THEN
        CREATE INDEX idx_token_log_usage_time ON token_usage_log (usage_time);
    END IF;
END$$

DELIMITER ;

CALL add_token_log_time_index();
DROP PROCEDURE IF EXISTS add_token_log_time_index;

-- ------------------------------------------------------------
-- 2. 默认 Token 月度配额（PLATFORM 级，0 = 不限制）
--    管理员可在「系统配置 → 运行参数」页面按作用域调整
-- ------------------------------------------------------------
INSERT INTO agent_config (config_key, config_value, scope, remark)
SELECT 'token_monthly_quota', '0', 'PLATFORM', '月度 Token 配额上限（万 tokens，0=不限制）'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM agent_config
    WHERE config_key = 'token_monthly_quota' AND scope = 'PLATFORM'
);

INSERT INTO agent_config (config_key, config_value, scope, remark)
SELECT 'token_quota_warn_percent', '80', 'PLATFORM', '配额告警阈值（百分比，达到后对话流注入告警事件）'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM agent_config
    WHERE config_key = 'token_quota_warn_percent' AND scope = 'PLATFORM'
);
