-- ------------------------------------------------------------
-- V29__fix_tavily_mcp_to_streamable_http.sql
-- 修复 tavily-mcp 传输方式：从 http 改为 streamable-http
-- 
-- 问题：虽然数据库配置为 transport='http',但 AgentScope MCP 客户端初始化时
--       仍返回 405 Method Not Allowed 错误
-- 
-- 原因分析：
-- 1. Tavily MCP 远程服务器使用 MCP Protocol 2025-06-18
-- 2. 需要完整的 Streamable HTTP 协议支持（双向通信）
-- 3. AgentScope 可能对 'http' 和 'streamable-http' 有不同实现
--    - 'http': 可能映射为旧的 SSE 或简单 HTTP 实现
--    - 'streamable-http': 完整的 MCP Streamable HTTP 协议
-- 
-- 解决方案：将传输方式改为 'streamable-http'
-- ------------------------------------------------------------

-- 更新 tavily-mcp 的传输方式
UPDATE mcp_server 
SET transport = 'streamable-http',
    remark = CONCAT(COALESCE(remark, ''), ' [V29修复：http→streamable-http]'),
    update_time = NOW()
WHERE name = 'tavily-mcp'
  AND transport = 'http';

-- 验证更新结果
SELECT id, name, transport, url, enabled, remark 
FROM mcp_server 
WHERE name = 'tavily-mcp';
