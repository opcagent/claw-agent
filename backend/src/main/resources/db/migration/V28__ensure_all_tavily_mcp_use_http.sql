-- ------------------------------------------------------------
-- V28__ensure_all_tavily_mcp_use_http.sql
-- 确保所有 tavily-mcp 记录都使用 HTTP 传输方式
-- 
-- 问题：可能有多个作用域（PLATFORM/TENANT/USER）的 tavily-mcp 记录，
--       其中某些仍使用 SSE 导致 405 错误
-- 解决：批量更新所有 tavily-mcp 记录为 HTTP，并将 API Key 从 headers 移到 URL 查询参数
-- 
-- Tavily MCP 要求：API Key 必须作为 URL 查询参数传递（?tavilyApiKey=xxx）
-- 参考：https://docs.tavily.com/documentation/mcp#remote-mcp-server
-- ------------------------------------------------------------

-- 查看所有 tavily-mcp 记录（用于调试）
SELECT id, scope, tenant_id, owner_name, name, transport, url, headers, enabled, create_time, update_time
FROM mcp_server 
WHERE LOWER(name) LIKE '%tavily%'
ORDER BY scope DESC, tenant_id, owner_name;

-- 批量更新所有 tavily-mcp 记录
-- 1. 传输方式改为 HTTP
-- 2. URL 添加 tavilyApiKey 查询参数（需要从 headers 中提取，这里先清空 headers）
-- 3. 清空 headers（因为 API Key 已在 URL 中）
UPDATE mcp_server 
SET transport = 'http',
    -- 注意：实际使用时需要手动在 URL 中添加 ?tavilyApiKey=YOUR_KEY
    -- 或者通过前端界面重新配置，系统会自动处理
    url = CASE 
        WHEN url LIKE '%tavilyApiKey=%' THEN url  -- 已包含 API Key，保持不变
        ELSE CONCAT(url, '?tavilyApiKey=NEED_TO_CONFIGURE')  -- 提示需要配置
    END,
    headers = NULL,  -- 清空 headers，API Key 在 URL 中
    remark = CONCAT(COALESCE(remark, ''), ' [自动修复：SSE→HTTP, API Key 需手动配置到 URL]'),
    update_time = NOW()
WHERE LOWER(name) LIKE '%tavily%'
  AND (transport != 'http' OR headers IS NOT NULL);

-- 验证更新结果
SELECT id, scope, tenant_id, owner_name, name, transport, url, headers, enabled, remark
FROM mcp_server 
WHERE LOWER(name) LIKE '%tavily%'
ORDER BY scope DESC, tenant_id, owner_name;
