-- V50：chat_message 复合索引优化
-- 消息列表查询按 session_id + create_time 排序，原仅有 idx_session(session_id) 单列索引，
-- 缺少复合索引导致 filesort；添加复合索引后查询可走索引覆盖扫描。
-- 兼容 MySQL 5.x：使用存储过程判断索引是否存在再创建

DROP PROCEDURE IF EXISTS add_chat_message_composite_index;

DELIMITER $$

CREATE PROCEDURE add_chat_message_composite_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'chat_message'
          AND INDEX_NAME = 'idx_message_session_time'
    ) THEN
        ALTER TABLE chat_message ADD INDEX idx_message_session_time (session_id, create_time);
    END IF;
END$$

DELIMITER ;

CALL add_chat_message_composite_index();
DROP PROCEDURE IF EXISTS add_chat_message_composite_index;
