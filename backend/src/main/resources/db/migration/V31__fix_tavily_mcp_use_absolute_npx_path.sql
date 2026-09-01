-- ------------------------------------------------------------
-- V31__fix_tavily_mcp_use_absolute_npx_path.sql
-- 修复 tavily-mcp:使用 npx 绝对路径
-- 
-- 问题: Java 进程启动时找不到 npx 命令
-- 原因: npx 未添加到系统 PATH,或 Java 子进程环境变量继承问题
-- 解决: 使用 npx 的绝对路径 D:\environment\nodejs\npx.cmd
-- ------------------------------------------------------------

-- 更新 tavily-mcp 配置为使用 npx 绝对路径
UPDATE mcp_server 
SET transport = 'stdio',
    command = 'D:\environment\nodejs\npx.cmd',
    args = '["-y", "mcp-remote", "https://mcp.tavily.com/mcp/?tavilyApiKey=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"]',
    url = NULL,
    headers = NULL,
    remark = CONCAT(COALESCE(remark, ''), ' [V31修复：使用 npx 绝对路径]'),
    update_time = NOW()
WHERE name = 'tavily-mcp';

-- 验证更新结果
SELECT id, name, transport, command, args, enabled, remark 
FROM mcp_server 
WHERE name = 'tavily-mcp';
