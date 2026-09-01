-- V37__cleanup_duplicate_tavily_mcp_records.sql
-- 清理 Tavily MCP 重复配置记录
-- 
-- 背景: V27-V31 多次迭代修复导致可能存在多条 tavily-mcp 记录
-- 目标: 只保留 PLATFORM 作用域的最终配置,删除其他冗余记录
-- 
-- 问题演进历史:
-- V27: SSE → HTTP
-- V28: 批量更新所有 tavily-mcp 为 HTTP
-- V29: HTTP → Streamable HTTP
-- V30: 使用 mcp-remote 桥接 (stdio)
-- V31: 使用 npx 绝对路径 (最终版)

-- ============================================================
-- Part 1: 查看当前所有 tavily-mcp 记录(用于审计)
-- ============================================================

SELECT 
    id,
    scope,
    tenant_id,
    owner_name,
    name,
    transport,
    command,
    args,
    enabled,
    create_time,
    update_time,
    remark
FROM mcp_server 
WHERE LOWER(name) LIKE '%tavily%'
ORDER BY scope DESC, create_time;

-- ============================================================
-- Part 2: 清理冗余记录
-- ============================================================

-- 2.1 删除非 PLATFORM 作用域的冗余记录(保留每个作用域的最新一条)
DELETE FROM mcp_server 
WHERE LOWER(name) LIKE '%tavily%'
  AND scope != 'PLATFORM'
  AND id NOT IN (
      SELECT latest.id FROM (
          SELECT MAX(id) as id 
          FROM mcp_server 
          WHERE LOWER(name) LIKE '%tavily%' 
            AND scope != 'PLATFORM'
          GROUP BY scope, tenant_id, owner_name
      ) AS latest
  );

-- 2.2 确保 PLATFORM 作用域只保留最新的一条记录
DELETE FROM mcp_server 
WHERE LOWER(name) LIKE '%tavily%'
  AND scope = 'PLATFORM'
  AND id NOT IN (
      SELECT latest.id FROM (
          SELECT MAX(id) as id 
          FROM mcp_server 
          WHERE LOWER(name) LIKE '%tavily%' 
            AND scope = 'PLATFORM'
      ) AS latest
  );

-- ============================================================
-- Part 3: 验证清理结果
-- ============================================================

-- 3.1 统计各作用域的记录数
SELECT 
    scope,
    COUNT(*) as record_count,
    GROUP_CONCAT(id ORDER BY id) as ids
FROM mcp_server 
WHERE LOWER(name) LIKE '%tavily%'
GROUP BY scope
ORDER BY scope DESC;

-- 3.2 查看所有 tavily-mcp 记录的详细信息
SELECT 
    id,
    scope,
    tenant_id,
    owner_name,
    name,
    transport,
    command,
    LEFT(args, 50) as args_preview,
    enabled,
    remark
FROM mcp_server 
WHERE LOWER(name) LIKE '%tavily%'
ORDER BY scope DESC, id;

-- 3.3 确认最终配置正确性
SELECT 
    CASE 
        WHEN COUNT(*) = 1 THEN '✅ 清理成功: 只剩 1 条 PLATFORM 记录'
        WHEN COUNT(*) > 1 THEN '⚠️ 警告: 仍有 ' + CAST(COUNT(*) AS CHAR) + ' 条记录'
        ELSE '❌ 错误: 所有 tavily-mcp 记录都被删除了'
    END as validation_result
FROM mcp_server 
WHERE LOWER(name) LIKE '%tavily%'
  AND scope = 'PLATFORM';
