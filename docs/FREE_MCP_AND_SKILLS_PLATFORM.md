# 免费 MCP 服务器与 Skill 平台化方案

## 📋 核心原则

### ✅ 什么是"免费"的?

1. **完全免费无限制**: 开源项目、自托管服务
2. **免费额度充足**: 个人开发者/小团队够用 (每月 1000+ 次调用)
3. **无需信用卡**: 注册即用,不绑定支付方式
4. **社区维护**: 活跃度高,文档完善

### ❌ 排除项

- 需要企业级订阅的服务 (如 OpenAI API,按 Token 计费)
- 免费额度极低 (<100 次/月)
- 需要复杂配置或自建基础设施

---

## 🎯 推荐的免费 MCP 服务器 (按优先级排序)

### 1️⃣ **文件系统操作** ⭐⭐⭐⭐⭐ (必装)

#### mcp-server-filesystem (官方)
- **传输协议**: `stdio`
- **安装命令**: `npx -y @modelcontextprotocol/server-filesystem /path/to/workspace`
- **功能**: 读取/写入/列出文件,支持 glob 模式
- **免费程度**: ✅ 完全免费 (本地运行)
- **适用场景**: Agent 读写工作区文件、代码编辑、笔记管理
- **配置示例**:
```sql
INSERT INTO mcp_server (name, transport, command, args, enabled, scope) VALUES 
('mcp-filesystem', 'stdio', 'D:\environment\nodejs\npx.cmd', 
 '["-y", "@modelcontextprotocol/server-filesystem", "d:/claw-agent/.agentscope/workspace"]', 
 1, 'PLATFORM');
```

#### 优势
- 零成本,本地运行
- AgentScope Harness 已内置类似功能 (`read_file`, `write_file`)
- 可作为备用方案

---

### 2️⃣ **浏览器自动化** ⭐⭐⭐⭐⭐ (强烈推荐)

#### chrome-devtools-mcp
- **传输协议**: `stdio`
- **安装命令**: `npm install -g chrome-devtools-mcp`
- **功能**: 控制 Chrome 浏览器,截图、点击、填写表单、执行 JS
- **免费程度**: ✅ 完全免费 (本地 Chrome)
- **适用场景**: 网页抓取、UI 测试、自动化操作
- **配置示例**:
```sql
INSERT INTO mcp_server (name, transport, command, args, enabled, scope) VALUES 
('chrome-devtools', 'stdio', 'node', 
 '["/path/to/chrome-devtools-mcp/dist/index.js"]', 
 1, 'PLATFORM');
```

#### playwright-mcp
- **传输协议**: `stdio`
- **安装命令**: `npx -y @playwright/mcp@latest`
- **功能**: 跨浏览器自动化 (Chrome/Firefox/Safari)
- **免费程度**: ✅ 完全免费
- **优势**: 比 Puppeteer 更稳定,支持多浏览器

---

### 3️⃣ **GitHub 集成** ⭐⭐⭐⭐ (开发必备)

#### github-mcp-server
- **传输协议**: `stdio` 或 `http`
- **安装命令**: `npx -y @octokit/mcp-server`
- **功能**: 读取仓库、创建 Issue/PR、搜索代码
- **免费程度**: ✅ GitHub 免费套餐 (5000 次/小时 API 调用)
- **认证**: Personal Access Token (PAT),无需信用卡
- **配置示例**:
```sql
INSERT INTO mcp_server (name, transport, command, args, env, enabled, scope) VALUES 
('github-mcp', 'stdio', 'D:\environment\nodejs\npx.cmd', 
 '["-y", "@octokit/mcp-server"]',
 '{"GITHUB_TOKEN": "ghp_xxx"}',
 1, 'PLATFORM');
```

#### 适用场景
- 代码审查助手
- 自动创建 Issue
- 搜索开源项目

---

### 4️⃣ **PostgreSQL 数据库** ⭐⭐⭐⭐ (数据查询)

#### postgres-mcp
- **传输协议**: `stdio`
- **安装命令**: `npx -y @modelcontextprotocol/server-postgres postgresql://user:pass@localhost/db`
- **功能**: 执行 SQL 查询、查看表结构
- **免费程度**: ✅ 完全免费 (连接本地/远程数据库)
- **适用场景**: 数据分析、报表生成、数据库管理
- **注意**: 需配置只读账号,避免误删数据

---

### 5️⃣ **SQLite 数据库** ⭐⭐⭐ (轻量级)

#### sqlite-mcp
- **传输协议**: `stdio`
- **安装命令**: `npx -y @modelcontextprotocol/server-sqlite /path/to/database.db`
- **功能**: 查询 SQLite 数据库
- **免费程度**: ✅ 完全免费
- **适用场景**: 本地数据存储、小型应用

---

### 6️⃣ **Git 版本控制** ⭐⭐⭐⭐ (代码管理)

#### git-mcp
- **传输协议**: `stdio`
- **安装命令**: `npx -y @modelcontextprotocol/server-git`
- **功能**: 查看提交历史、差异对比、分支管理
- **免费程度**: ✅ 完全免费
- **适用场景**: 代码审查、变更追踪

---

### 7️⃣ **记忆/向量搜索** ⭐⭐⭐⭐⭐ (Agent 核心能力)

#### memory-mcp (AgentScope 内置)
- **传输协议**: 内置 (无需额外配置)
- **功能**: 长期记忆存储、语义搜索
- **免费程度**: ✅ 完全免费
- **已集成**: AgentScope Harness 自带 `memory_search`, `memory_get`, `memory_save`

#### chroma-mcp
- **传输协议**: `stdio`
- **安装命令**: `pip install chromadb && npx -y chroma-mcp`
- **功能**: 向量数据库,语义搜索文档
- **免费程度**: ✅ 完全免费 (本地运行)
- **适用场景**: RAG (检索增强生成)、知识库问答

---

### 8️⃣ **天气查询** ⭐⭐⭐ (实用工具)

#### weather-mcp
- **传输协议**: `stdio`
- **安装命令**: `npx -y @modelcontextprotocol/server-weather`
- **API Key**: OpenWeatherMap (免费套餐: 1000 次/天)
- **功能**: 查询实时天气、预报
- **配置示例**:
```sql
INSERT INTO mcp_server (name, transport, command, args, env, enabled, scope) VALUES 
('weather-mcp', 'stdio', 'D:\environment\nodejs\npx.cmd', 
 '["-y", "@modelcontextprotocol/server-weather"]',
 '{"OPENWEATHER_API_KEY": "xxx"}',
 1, 'PLATFORM');
```

---

### 9️⃣ **日历/日程** ⭐⭐⭐ (生产力)

#### google-calendar-mcp
- **传输协议**: `stdio`
- **安装命令**: `npx -y @modelcontextprotocol/server-google-calendar`
- **认证**: OAuth 2.0 (Google 账号)
- **免费程度**: ✅ Google 免费套餐 (100 次/天)
- **功能**: 查看日程、创建事件

---

### 🔟 **Slack/Discord 消息** ⭐⭐ (团队协作)

#### slack-mcp
- **传输协议**: `stdio`
- **安装命令**: `npx -y @modelcontextprotocol/server-slack`
- **认证**: Slack Bot Token
- **免费程度**: ✅ Slack 免费版
- **功能**: 发送消息、读取频道

---

## 🛠️ 推荐的 Skills (技能包)

### 1️⃣ **web-digest** (已有) ⭐⭐⭐⭐⭐
- **位置**: `D:/claw-agent/.agentscope/workspace/skills/web-digest/SKILL.md`
- **功能**: 网页内容提取、摘要生成
- **免费程度**: ✅ 完全免费
- **优化建议**: 扩展为通用网页爬虫,支持批量 URL 处理

---

### 2️⃣ **code-review** (新增) ⭐⭐⭐⭐⭐
- **功能**: 代码审查、最佳实践检查、安全漏洞扫描
- **实现方式**: 结合 ESLint/Pylint + AI 分析
- **免费程度**: ✅ 完全免费 (本地工具)
- **Skill 描述**:
```markdown
# Code Review Skill

当用户请求代码审查时:
1. 使用 linter 工具扫描代码 (ESLint for JS, Pylint for Python)
2. 分析常见问题: 命名规范、复杂度、潜在 bug
3. 提供改进建议和重构方案
4. 标注严重程度 (Critical/Warning/Info)
```

---

### 3️⃣ **data-analysis** (新增) ⭐⭐⭐⭐
- **功能**: CSV/Excel 数据分析、可视化图表生成
- **依赖**: pandas, matplotlib (Python)
- **免费程度**: ✅ 完全免费
- **适用场景**: 销售数据、用户行为分析

---

### 4️⃣ **seo-audit** (新增) ⭐⭐⭐
- **功能**: SEO 审计、页面性能分析
- **免费工具**: Lighthouse CLI, PageSpeed Insights API (免费)
- **输出**: 评分、改进建议、关键指标

---

### 5️⃣ **translation** (新增) ⭐⭐⭐⭐
- **功能**: 多语言翻译 (中英日韩)
- **免费 API**: LibreTranslate (开源,自托管)
- **替代方案**: DeepL (免费套餐 500k 字符/月)

---

### 6️⃣ **image-processing** (新增) ⭐⭐⭐
- **功能**: 图片压缩、格式转换、尺寸调整
- **免费库**: Sharp (Node.js), Pillow (Python)
- **适用场景**: 头像上传、缩略图生成

---

### 7️⃣ **regex-tester** (新增) ⭐⭐⭐
- **功能**: 正则表达式测试、解释、生成
- **免费程度**: ✅ 完全免费
- **适用场景**: 表单验证、文本提取

---

### 8️⃣ **api-doc-generator** (新增) ⭐⭐⭐⭐
- **功能**: 从代码自动生成 API 文档 (OpenAPI/Swagger)
- **免费工具**: Swagger Codegen, tsoa (TypeScript)
- **适用场景**: 后端接口文档维护

---

## 🚀 平台化实施方案

### 阶段 1: 基础 MCP 服务器 (本周完成)

#### 优先级排序
1. **文件系统** (已有 Harness 内置,可选)
2. **Chrome DevTools** (浏览器自动化)
3. **GitHub** (代码管理)
4. **PostgreSQL/SQLite** (数据查询)

#### 实施步骤

**Step 1: 创建 Flyway 迁移脚本**
```sql
-- V33__add_free_mcp_servers.sql

-- 1. Chrome DevTools MCP
INSERT INTO mcp_server (name, transport, command, args, enabled, scope, remark) VALUES 
('chrome-devtools', 'stdio', 'node', 
 '["/absolute/path/to/chrome-devtools-mcp/dist/index.js"]', 
 1, 'PLATFORM', '浏览器自动化: 截图、点击、填表');

-- 2. GitHub MCP
INSERT INTO mcp_server (name, transport, command, args, env, enabled, scope, remark) VALUES 
('github-mcp', 'stdio', 'D:\environment\nodejs\npx.cmd', 
 '["-y", "@octokit/mcp-server"]',
 '{"GITHUB_TOKEN": "ghp_xxx"}',
 1, 'PLATFORM', 'GitHub 集成: Issue/PR/代码搜索');

-- 3. PostgreSQL MCP (可选,需先有数据库)
INSERT INTO mcp_server (name, transport, command, args, enabled, scope, remark) VALUES 
('postgres-mcp', 'stdio', 'D:\environment\nodejs\npx.cmd', 
 '["-y", "@modelcontextprotocol/server-postgres", "postgresql://user:pass@localhost/db"]', 
 0, 'PLATFORM', 'PostgreSQL 查询 (默认禁用,需配置数据库)');
```

**Step 2: 前端 MCP 管理界面增强**
- 添加"推荐 MCP 服务器"列表
- 一键安装按钮 (自动填充配置)
- 状态检测 (检查 Node.js/npm 是否可用)

**Step 3: 权限配置**
- 在 `tool_permission` 表中登记新工具的 ALLOW/ASK 规则
- 例如: `chrome_devtools_screenshot` → ASK (危险操作需确认)

---

### 阶段 2: Skills 开发 (下周完成)

#### 开发流程

**1. code-review Skill**
```bash
cd D:/claw-agent/.agentscope/workspace/skills
mkdir code-review
cat > SKILL.md << 'EOF'
# Code Review Skill

## Usage Scenario
当用户请求代码审查、代码质量检查、安全审计时使用。

## Steps
1. 识别编程语言 (JS/TS/Python/Java)
2. 运行对应的 linter 工具
3. 分析输出结果
4. 按严重程度分类问题
5. 提供修复建议

## Notes
- Critical: 必须修复 (安全漏洞、逻辑错误)
- Warning: 建议修复 (代码异味、性能问题)
- Info: 可选优化 (命名规范、注释补充)
EOF
```

**2. data-analysis Skill**
```bash
mkdir data-analysis
cat > SKILL.md << 'EOF'
# Data Analysis Skill

## Usage Scenario
用户上传 CSV/Excel 文件,请求数据分析、趋势洞察、可视化图表时使用。

## Dependencies
- Python 3.8+
- pandas, matplotlib, seaborn

## Steps
1. 读取数据文件
2. 基本统计 (均值、中位数、标准差)
3. 缺失值分析
4. 相关性分析
5. 生成可视化图表 (折线图、柱状图、散点图)

## Output
- 统计摘要表格
- 关键洞察 (3-5 条)
- 图表文件路径
EOF
```

**3. 其他 Skills 同理创建**

---

### 阶段 3: 动态注册系统升级 (下下周完成)

#### 目标
让 MCP 服务器和 Skills 也能像 Tool 一样动态注册、启用/禁用。

#### 实施方案

**1. 扩展 `ToolRegistry`**
```java
@Component
public class ToolRegistry {
    
    // 现有: @ToolSet 注解的工具
    private Map<String, ToolSetMetadata> toolSetsCache;
    
    // 新增: MCP 服务器元数据
    private Map<String, McpServerMetadata> mcpServersCache;
    
    // 新增: Skills 元数据
    private Map<String, SkillMetadata> skillsCache;
    
    /**
     * 启动时扫描三类资源:
     * 1. classpath 下的 @ToolSet 类
     * 2. 数据库 mcp_server 表
     * 3. workspace/skills/ 目录下的 SKILL.md
     */
    @PostConstruct
    public void init() {
        scanAndRegisterToolSets();
        scanAndRegisterMcpServers();
        scanAndRegisterSkills();
    }
}
```

**2. 新增 REST API**
```java
@RestController
@RequestMapping("/api/platform-resources")
public class PlatformResourceController {
    
    // 获取所有 MCP 服务器 (含状态)
    @GetMapping("/mcp-servers")
    public Result<List<McpServerMetadata>> listMcpServers() { ... }
    
    // 启用/禁用 MCP 服务器
    @PostMapping("/mcp-servers/{name}/enable")
    public Result<Void> enableMcpServer(@PathVariable String name) { ... }
    
    // 获取所有 Skills
    @GetMapping("/skills")
    public Result<List<SkillMetadata>> listSkills() { ... }
    
    // 安装新 Skill (从 GitHub URL)
    @PostMapping("/skills/install")
    public Result<Void> installSkill(@RequestBody InstallSkillRequest req) { ... }
}
```

**3. 前端管理界面**
- MCP 服务器列表页 (卡片式展示)
- Skills 市场 (分类浏览、搜索、一键安装)
- 资源使用情况面板 (哪些工具被频繁调用)

---

## 💰 成本估算

### 完全免费 (0 元)
| 资源 | 数量 | 成本 |
|------|------|------|
| 文件系统 MCP | 1 | ✅ 免费 |
| Chrome DevTools MCP | 1 | ✅ 免费 |
| Git MCP | 1 | ✅ 免费 |
| SQLite MCP | 1 | ✅ 免费 |
| web-digest Skill | 1 | ✅ 免费 |
| code-review Skill | 1 | ✅ 免费 |
| **小计** | **6** | **¥0** |

### 免费额度充足 (个人够用)
| 资源 | 免费额度 | 成本 |
|------|---------|------|
| GitHub MCP | 5000 次/小时 | ✅ 免费 |
| Tavily Search | 1000 次/月 | ✅ 免费 |
| Brave Search | ~1000 次/月 | ✅ 免费 |
| OpenWeatherMap | 1000 次/天 | ✅ 免费 |
| **小计** | **-** | **¥0** |

### 可选付费 (按需)
| 资源 | 免费额度 | 付费方案 | 建议 |
|------|---------|---------|------|
| DeepL Translation | 500k 字符/月 | €5.99/月 (无限制) | 初期用免费版 |
| Google Calendar | 100 次/天 | $6/用户/月 | 个人够用 |
| **小计** | **-** | **¥0-50/月** | **可选** |

**总成本**: **¥0 - ¥50/月** (完全取决于是否使用付费服务)

---

## ⚠️ 注意事项

### 1. 安全性
- **MCP 服务器权限**: 文件系统 MCP 需限制工作区目录,避免访问敏感文件
- **API Key 管理**: 使用环境变量或加密存储,不要硬编码
- **OAuth Token**: 定期轮换,最小权限原则

### 2. 性能
- **本地 MCP**: stdio 协议启动 Node.js 进程有开销 (~1-2s),建议常驻
- **远程 MCP**: HTTP 延迟较高,设置合理超时时间
- **Skills 依赖**: 首次安装需下载 Python/Node.js 包,耗时较长

### 3. 兼容性
- **Node.js 版本**: 要求 v18+,旧版本可能报错
- **Python 版本**: Skills 通常要求 Python 3.8+
- **操作系统**: Windows 路径分隔符 `\` vs Linux `/`,需适配

### 4. 维护
- **版本锁定**: MCP 服务器指定版本号 (如 `tavily-mcp@0.1.3`),避免 breaking changes
- **监控日志**: 记录 MCP 初始化失败原因,便于排查
- **文档更新**: 新增 MCP/Skill 后同步更新本文档

---

## 📊 预期收益

### 对用户的价值
1. **能力提升**: Agent 可操作浏览器、管理代码、查询数据库
2. **零成本**: 大部分功能免费,个人开发者友好
3. **易扩展**: 一键安装新 MCP/Skill,无需重启服务

### 对平台的价值
1. **差异化竞争**: 丰富的工具生态吸引用户
2. **用户粘性**: 工作区文件、Skills 沉淀形成壁垒
3. **商业化潜力**: 未来可推出高级 MCP (如企业级数据库连接器)

---

## 🎯 下一步行动

### 立即执行 (今天)
1. ✅ 阅读本方案,确认优先级
2. ⏳ 选择首批安装的 3 个 MCP 服务器 (建议: Chrome DevTools + GitHub + SQLite)
3. ⏳ 创建 V33 Flyway 迁移脚本

### 本周完成
1. ⏳ 实现 MCP 服务器动态注册 API
2. ⏳ 开发 2 个 Skills (code-review + data-analysis)
3. ⏳ 前端 MCP 管理界面原型

### 下周完成
1. ⏳ 完整测试所有 MCP 服务器
2. ⏳ 编写用户文档 (如何配置 API Key)
3. ⏳ 性能优化 (MCP 进程池、缓存机制)

---

## 🔗 参考资源

- [MCP 官方文档](https://modelcontextprotocol.io/)
- [AgentScope Java 文档](https://agentscope.io/)
- [Awesome MCP Servers](https://github.com/modelcontextprotocol/servers)
- [Tavily MCP 配置指南](https://docs.tavily.com/documentation/mcp)
