# Token 使用统计功能集成指南

##  概述

本文档说明如何在 claw-agent 平台中集成和使用 Token 使用统计功能。

---

## ✅ 已完成的工作

### 1. 数据库层 (Flyway V32)

**表结构**:
- `token_usage_log`: 详细流水表,记录每次模型调用的 Token 消耗
- `token_usage_summary`: 月度汇总表,自动触发器维护

**关键字段**:
```sql
-- token_usage_log
user_id, tenant_id, username, session_id,
provider, model_name, 
prompt_tokens, completion_tokens, total_tokens,
usage_time, create_time

-- token_usage_summary  
user_id, tenant_id, username,
period_type (daily/monthly/yearly), period_start, period_end,
total_prompt_tokens, total_completion_tokens, total_tokens, request_count
```

**自动触发器**:
- `trg_update_token_summary`: 插入流水时自动更新汇总表

### 2. 后端 API (V34)

**TokenUsageService**:
- `recordUsage()`: 记录单次调用
- `getCurrentMonthSummary()`: 本月汇总
- `getMonthSummary()`: 指定月份
- `getRecentMonthsSummary()`: 最近 N 个月趋势
- `getTenantUsersSummary()`: 租户用户排行 (管理员)

**TokenUsageController**:
- `GET /api/token-usage/current-month` - 本月汇总
- `GET /api/token-usage/month/{year}/{month}` - 指定月份
- `GET /api/token-usage/recent-months?months=6` - 趋势数据
- `GET /api/token-usage/logs?limit=50` - 流水明细
- `GET /api/token-usage/admin/tenant-users` - 管理员视图
- `POST /api/token-usage/test-record` - **测试接口** (手动记录)

**菜单配置**:
- 菜单 ID: `9000`
- 父菜单: "平台治理" (ID=2)
- 路径: `/token-usage`
- 权限: USER + ADMIN

### 3. 前端页面

**文件**: `frontend/src/app/token-usage/page.tsx` (463行)

**功能模块**:
1. **本月汇总卡片**: 总 Token、请求次数、周期范围、最后更新
2. **趋势分析 Tab**: 近 6 个月柱状图 + 提供商占比饼图
3. **使用流水 Tab**: 最近 50 条详细记录表格
4. **管理员视图 Tab**: 租户内用户排行 (仅 ADMIN 可见)

**技术栈**:
- Recharts (图表库)
- 自研 Tabs 组件
- 泛型 API 封装 (`api.get<T>()`)

---

## 🚀 快速开始

### Step 1: 执行数据库迁移

```bash
cd d:\claw-agent\backend
mvn flyway:migrate
```

这将执行:
- V32: 创建表和触发器
- V34: 插入菜单项和角色授权

### Step 2: 启动后端服务

```bash
$env:JAVA_HOME = "D:\environment\jdk-21.0.8"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd d:\claw-agent\backend
java -jar target/backend-1.0.0-SNAPSHOT.jar
```

### Step 3: 访问前端页面

1. 打开浏览器: http://localhost:3000
2. 登录账号
3. 在左侧导航栏找到 **"平台治理"** → **"Token 统计"**
4. 点击即可查看 Token 使用详情

### Step 4: 验证 Token 自动记录

✅ **自动 Token 记录已完成**。通过 `ModelCallEndEvent` 事件驱动，每次模型调用完成后自动提取 `ChatUsage` 并异步落库，无需手动调用测试接口。

进行对话后，刷新 Token 统计页面即可看到自动记录的流水数据。

如需手动验证链路，仍可使用测试接口：

```bash
curl -X POST http://localhost:8080/api/token-usage/test-record \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"
```

---

## ✅ 自动 Token 记录（已完成）

### 已实现方案：ModelCallEndEvent 事件驱动

通过 AgentScope 的 `ModelCallEndEvent` 事件机制实现自动 Token 记录：

**实现位置**: `AgentService.toChatEvent()` 处理 `ModelCallEndEvent` 事件

**核心逻辑**:
- 模型调用完成时自动触发 `ModelCallEndEvent`
- 提取 `ChatUsage`（inputTokens / outputTokens）
- 异步写入数据库：`Mono.fromRunnable().subscribeOn(boundedElastic).subscribe()`
- 回合级缓存：`providerName/modelName` 在对话开始时解析并缓存，避免重复查库+解密 API Key

**技术要点**:
- 零侵入：无需修改业务代码或包装 Model
- 不阻塞：异步写入不影响 SSE 流式输出性能
- 全覆盖：所有模型调用均自动记录

---

## 📊 验证清单

- [ ] V32 迁移脚本已执行 (表 + 触发器)
- [ ] V34 迁移脚本已执行 (菜单 + 权限)
- [ ] 后端服务正常启动 (端口 8080)
- [ ] 前端页面可访问 (/token-usage)
- [ ] 左侧导航栏显示 "Token 统计" 菜单项
- [ ] 测试接口 `/api/token-usage/test-record` 返回成功
- [ ] Token 统计页面显示测试记录
- [ ] 本月汇总卡片数据正确
- [ ] 趋势图正常渲染
- [ ] 流水表格显示记录
- [ ] 管理员视图可见 (ADMIN 角色)

---

## 🐛 常见问题

### Q1: 看不到 "Token 统计" 菜单项

**原因**: V34 迁移脚本未执行或 parent_id 错误

**解决**:
```sql
-- 检查菜单是否存在
SELECT * FROM sys_menu WHERE id = 9000;

-- 如果不存在,手动插入
INSERT INTO sys_menu (id, parent_id, menu_name, path, ...) VALUES (...);

-- 如果存在但 parent_id 错误
UPDATE sys_menu SET parent_id = 2 WHERE id = 9000;
```

### Q2: 测试接口返回 401 Unauthorized

**原因**: 未携带 JWT Token

**解决**:
1. 先登录获取 Token
2. 在请求头中添加: `Authorization: Bearer YOUR_TOKEN`

### Q3: Token 统计页面空白或报错

**原因**: 前端构建未完成或 API 调用失败

**解决**:
```bash
cd d:\claw-agent\frontend
npm run build
npm run dev
```

检查浏览器控制台是否有 API 调用错误。

### Q4: 流水表中没有数据

**原因**: 测试接口未调用或记录失败

**解决**:
1. 调用测试接口: `POST /api/token-usage/test-record`
2. 检查后端日志是否有异常
3. 查询数据库: `SELECT * FROM token_usage_log ORDER BY id DESC LIMIT 10;`

---

## 📝 下一步工作

1. ~~**完成自动 Token 记录**~~: ✅ 已通过 ModelCallEndEvent 实现
2. **添加成本计算**: 根据模型单价计算每次调用的费用 (CNY)
3. **设置额度预警**: 当用户 Token 消耗超过阈值时发送通知
4. **导出报表**: 支持 CSV/Excel 导出月度使用情况
5. **可视化优化**: 添加更多图表类型 (折线图、热力图等)

---

**最后更新**: 2026-09-03  
**维护者**: claw-agent 团队
