-- ------------------------------------------------------------
-- V25：回填 chat_message 历史数据的审计字段
-- 此前消息落库走异步线程，UserContextHolder（ThreadLocal）不跨线程，
-- 审计填充器取不到操作人，导致存量记录 creator / updater / creator_id / updater_id 全空。
-- 代码侧已修复（异步线程内桥接用户上下文），本脚本按消息归属用户回填存量数据：
-- 消息即用户本人会话产生，归属用户即创建人。
-- ------------------------------------------------------------

-- 创建人/修改人用户名：直接取消息归属用户
UPDATE chat_message
SET creator = username,
    updater = username
WHERE creator IS NULL;

-- 创建人/修改人用户ID：经 sys_user 反查（删除的用户保持为空，不阻断）
UPDATE chat_message m
    INNER JOIN sys_user u ON u.username = m.username
SET m.creator_id = u.id,
    m.updater_id = u.id
WHERE m.creator_id IS NULL;
