-- ------------------------------------------------------------
-- V33__add_free_mcp_servers.sql
-- 添加免费 MCP 服务器 (PLATFORM 作用域)
-- 
-- 包含:
-- 1. Git MCP - 版本控制 (默认启用)
-- 2. GitHub MCP - 代码管理 (需配置 Token)
-- 3. Chrome DevTools MCP - 浏览器自动化 (需安装 npm 包)
-- 4. SQLite MCP - 轻量级数据库查询 (需创建数据库文件)
-- 5. PostgreSQL MCP - 企业级数据库 (可选)
-- 
-- 注意: 所有 MCP 均为完全免费,无需 API Key (除 GitHub 需 PAT)
-- ------------------------------------------------------------

-- ============================================================
-- 1. Git MCP (版本控制) - 默认启用
-- ============================================================
INSERT INTO mcp_server (
    name, transport, command, args, url, headers, env, 
    enabled, scope, tenant_id, remark, create_time, update_time
) VALUES (
    'git-mcp',
    'stdio',
    'D:\environment\nodejs\npx.cmd',
    '["-y", "@modelcontextprotocol/server-git", "--repository", "d:/claw-agent"]',
    NULL, NULL, NULL,
    1, -- 默认启用 (Git 通常已安装)
    'PLATFORM', 0,  -- tenant_id=0 表示平台级配置
    'Git 版本控制: 查看提交历史、差异对比、分支管理',
    NOW(), NOW()
) ON DUPLICATE KEY UPDATE
    remark = VALUES(remark),
    update_time = NOW();

-- ============================================================
-- 2. GitHub MCP (代码管理) - 需配置 Token
-- ============================================================
INSERT INTO mcp_server (
    name, transport, command, args, url, headers, env, 
    enabled, scope, tenant_id, remark, create_time, update_time
) VALUES (
    'github-mcp',
    'stdio',
    'D:\environment\nodejs\npx.cmd',
    '["-y", "@octokit/mcp-server"]',
    NULL, NULL,
    '{"GITHUB_TOKEN": "ghp_YOUR_PERSONAL_ACCESS_TOKEN"}',
    0, -- 默认禁用,需配置 GITHUB_TOKEN
    'PLATFORM', 0,
    'GitHub 集成: 读取仓库、创建 Issue/PR、搜索代码。需在 env 中配置 GITHUB_TOKEN',
    NOW(), NOW()
) ON DUPLICATE KEY UPDATE
    remark = VALUES(remark),
    update_time = NOW();

-- ============================================================
-- 3. Chrome DevTools MCP (浏览器自动化) - 需安装 npm 包
-- ============================================================
INSERT INTO mcp_server (
    name, transport, command, args, url, headers, env, 
    enabled, scope, tenant_id, remark, create_time, update_time
) VALUES (
    'chrome-devtools',
    'stdio',
    'node',
    '["/absolute/path/to/chrome-devtools-mcp/dist/index.js"]',
    NULL, NULL, NULL,
    0, -- 默认禁用,需先安装 npm 包
    'PLATFORM', 0,
    'Chrome 浏览器自动化: 截图、点击、填表、执行 JS。需先运行: npm install -g chrome-devtools-mcp',
    NOW(), NOW()
) ON DUPLICATE KEY UPDATE
    remark = VALUES(remark),
    update_time = NOW();

-- ============================================================
-- 4. SQLite MCP (轻量级数据库) - 需创建数据库文件
-- ============================================================
INSERT INTO mcp_server (
    name, transport, command, args, url, headers, env, 
    enabled, scope, tenant_id, remark, create_time, update_time
) VALUES (
    'sqlite-mcp',
    'stdio',
    'D:\environment\nodejs\npx.cmd',
    '["-y", "@modelcontextprotocol/server-sqlite", "d:/claw-agent/data/app.db"]',
    NULL, NULL, NULL,
    0, -- 默认禁用,需先创建数据库文件
    'PLATFORM', 0,
    'SQLite 数据库查询。需先创建数据库文件: d:/claw-agent/data/app.db',
    NOW(), NOW()
) ON DUPLICATE KEY UPDATE
    remark = VALUES(remark),
    update_time = NOW();

-- ============================================================
-- 5. PostgreSQL MCP (可选,需先有数据库)
-- ============================================================
INSERT INTO mcp_server (
    name, transport, command, args, url, headers, env, 
    enabled, scope, tenant_id, remark, create_time, update_time
) VALUES (
    'postgres-mcp',
    'stdio',
    'D:\environment\nodejs\npx.cmd',
    '["-y", "@modelcontextprotocol/server-postgres", "postgresql://user:pass@localhost/db"]',
    NULL, NULL, NULL,
    0, -- 默认禁用,需配置数据库连接
    'PLATFORM', 0,
    'PostgreSQL 数据库查询。需在 args 中配置连接字符串',
    NOW(), NOW()
) ON DUPLICATE KEY UPDATE
    remark = VALUES(remark),
    update_time = NOW();

-- ============================================================
-- 验证插入结果
-- ============================================================
SELECT 
    id, name, transport, enabled, scope,
    LEFT(remark, 50) AS remark_preview
FROM mcp_server 
WHERE scope = 'PLATFORM'
ORDER BY id;
