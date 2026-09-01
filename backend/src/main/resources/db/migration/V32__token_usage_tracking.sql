-- ------------------------------------------------------------
-- V32__token_usage_tracking.sql
-- Token 使用追踪系统
-- 
-- 功能:
-- 1. token_usage_log: 记录每次模型调用的详细流水
-- 2. token_usage_summary: 按用户+周期汇总 Token 使用量
-- ------------------------------------------------------------

-- ============================================================
-- 表1: Token 使用流水表
-- ============================================================
CREATE TABLE IF NOT EXISTS token_usage_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    
    -- 用户信息
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    
    -- 会话信息
    session_id VARCHAR(100) COMMENT '会话ID',
    
    -- 模型信息
    provider VARCHAR(50) NOT NULL COMMENT '模型提供商: openai/dashscope/ollama',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    
    -- Token 消耗
    prompt_tokens INT NOT NULL DEFAULT 0 COMMENT '提示词 Token 数',
    completion_tokens INT NOT NULL DEFAULT 0 COMMENT '回复 Token 数',
    total_tokens INT NOT NULL DEFAULT 0 COMMENT '总 Token 数',
    
    -- 请求信息
    request_id VARCHAR(100) COMMENT '请求ID(用于追踪)',
    tool_name VARCHAR(100) COMMENT '使用的工具名称(如果有)',
    
    -- 时间信息
    usage_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '使用时间',
    usage_date DATE GENERATED ALWAYS AS (DATE(usage_time)) STORED COMMENT '使用日期(计算字段)',
    
    -- 审计
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_date (user_id, usage_date),
    INDEX idx_tenant_date (tenant_id, usage_date),
    INDEX idx_provider (provider),
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token 使用流水表';

-- ============================================================
-- 表2: Token 使用汇总表(按用户+月份)
-- ============================================================
CREATE TABLE IF NOT EXISTS token_usage_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    
    -- 用户信息
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    
    -- 周期信息
    period_type VARCHAR(20) NOT NULL DEFAULT 'monthly' COMMENT '周期类型: daily/monthly/yearly',
    period_start DATE NOT NULL COMMENT '周期开始日期',
    period_end DATE NOT NULL COMMENT '周期结束日期',
    
    -- Token 统计
    total_prompt_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '累计提示词 Token',
    total_completion_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '累计回复 Token',
    total_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '累计总 Token',
    request_count INT NOT NULL DEFAULT 0 COMMENT '请求次数',
    
    -- 最后更新时间
    last_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_user_period (user_id, period_type, period_start),
    INDEX idx_tenant_period (tenant_id, period_type, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token 使用汇总表';

-- ============================================================
-- 触发器: 自动更新汇总表
-- ============================================================
DELIMITER $$

CREATE TRIGGER trg_update_token_summary
AFTER INSERT ON token_usage_log
FOR EACH ROW
BEGIN
    DECLARE v_period_start DATE;
    DECLARE v_period_end DATE;
    
    -- 计算当月起止日期
    SET v_period_start = DATE_FORMAT(NEW.usage_time, '%Y-%m-01');
    SET v_period_end = LAST_DAY(v_period_start);
    
    -- 插入或更新汇总表
    INSERT INTO token_usage_summary (
        user_id, tenant_id, username, period_type, period_start, period_end,
        total_prompt_tokens, total_completion_tokens, total_tokens, request_count
    ) VALUES (
        NEW.user_id, NEW.tenant_id, NEW.username, 'monthly', v_period_start, v_period_end,
        NEW.prompt_tokens, NEW.completion_tokens, NEW.total_tokens, 1
    )
    ON DUPLICATE KEY UPDATE
        total_prompt_tokens = total_prompt_tokens + NEW.prompt_tokens,
        total_completion_tokens = total_completion_tokens + NEW.completion_tokens,
        total_tokens = total_tokens + NEW.total_tokens,
        request_count = request_count + 1,
        last_update_time = NOW();
END$$

DELIMITER ;

-- ============================================================
-- 示例数据(可选,用于测试)
-- ============================================================
-- INSERT INTO token_usage_log 
-- (user_id, tenant_id, username, provider, model_name, prompt_tokens, completion_tokens, total_tokens, usage_time)
-- VALUES 
-- (1, 1, 'admin', 'openai', 'gpt-4', 100, 200, 300, NOW()),
-- (1, 1, 'admin', 'openai', 'gpt-4', 150, 250, 400, NOW());
