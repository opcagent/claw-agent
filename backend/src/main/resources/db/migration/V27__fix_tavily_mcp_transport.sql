-- ------------------------------------------------------------
-- V19__fix_tavily_mcp_transport.sql
-- 修复 tavily-mcp 传输方式错误
-- 
-- 问题：tavily-mcp MCP 服务器配置为 SSE 传输方式，但 Tavily MCP 服务器不支持 SSE（返回 405 Method Not Allowed）
-- 解决：将传输方式改为 HTTP（Tavily MCP 支持 Streamable HTTP 协议）
-- 
-- Tavily MCP 官方文档：https://github.com/tavily-ai/tavily-mcp
-- 支持的传输方式：HTTP / Streamable HTTP
-- 端点示例：https://mcp.tavily.com/mcp
-- ------------------------------------------------------------

-- 更新 tavily-mcp 的传输方式为 HTTP
UPDATE mcp_server 
SET transport = 'http',
    url = 'https://mcp.tavily.com/mcp',
    remark = '修复：从 SSE 改为 HTTP（Tavily MCP 不支持 SSE）'
WHERE name = 'tavily-mcp';

-- 验证修改结果
SELECT id, name, transport, url, enabled, remark 
FROM mcp_server 
WHERE name = 'tavily-mcp';
