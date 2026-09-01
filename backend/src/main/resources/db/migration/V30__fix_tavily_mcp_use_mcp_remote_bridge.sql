-- ------------------------------------------------------------
-- V30__fix_tavily_mcp_use_mcp_remote_bridge.sql
-- 修复 tavily-mcp:使用 mcp-remote 桥接远程服务器
-- 
-- 问题根因:
-- 1. AgentScope Java 2.0.0 + mcp-core 0.17.0 不支持直接连接远程 MCP 服务器
-- 2. 虽然数据库配置了 transport='streamable-http',但 AgentScope 仍使用 SSE 实现
-- 3. Tavily MCP 返回 405 Method Not Allowed
-- 
-- 解决方案:
-- 使用 mcp-remote 桥接工具,将远程服务器转换为本地 stdio 协议
-- - transport: 'stdio' (AgentScope 支持)
-- - command: 'npx'
-- - args: ['-y', 'mcp-remote', 'https://mcp.tavily.com/mcp/?tavilyApiKey=xxx']
-- 
-- 参考: https://docs.tavily.com/documentation/mcp#clients-that-dont-support-remote-mcps
-- ------------------------------------------------------------

-- 更新 tavily-mcp 配置为使用 mcp-remote 桥接
UPDATE mcp_server 
SET transport = 'stdio',
    command = 'npx',
    args = '["-y", "mcp-remote", "https://mcp.tavily.com/mcp/?tavilyApiKey=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"]',
    url = NULL,
    headers = NULL,
    remark = CONCAT(COALESCE(remark, ''), ' [V30修复：使用 mcp-remote 桥接远程服务器]'),
    update_time = NOW()
WHERE name = 'tavily-mcp';

-- 验证更新结果
SELECT id, name, transport, command, args, url, enabled, remark 
FROM mcp_server 
WHERE name = 'tavily-mcp';
