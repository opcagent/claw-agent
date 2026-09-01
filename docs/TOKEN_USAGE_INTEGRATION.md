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

### Step 4: 测试 Token 记录

由于自动提取逻辑尚未完成,先通过测试接口手动记录:

```bash
# 使用 curl 或 Postman
curl -X POST http://localhost:8080/api/token-usage/test-record \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": "已记录测试 Token 使用: 150 tokens"
}
```

然后刷新 Token 统计页面,应该能看到新记录的流水。

---

## 🔧 后续优化:自动 Token 记录

### 方案 A: AgentScope Middleware (推荐但复杂)

**优点**:
- 自动拦截所有模型调用
- 无需修改业务代码
- 集中管理

**缺点**:
- AgentScope 2.0 的 Middleware API 与我假设的不同
- 需要研究 `ModelCallMiddleware`、`MiddlewareContext`、`ModelResponse` 的正确用法
- 可能需要升级 AgentScope 版本或使用不同的扩展包

**实现步骤**:
1. 研究 AgentScope 2.0 官方文档中的 Middleware 示例
2. 确认 `ModelResponse` 是否包含 `usage` 字段
3. 实现正确的 `extractUserId()`、`extractSessionId()` 等方法
4. 在 `AgentRegistry` 中注册 Middleware

### 方案 B: 在 AgentService 中手动记录 (简单但侵入性强)

**优点**:
- 实现简单,立即可用
- 不依赖 AgentScope 内部 API

**缺点**:
- 需要修改 `AgentService.doChat()` 方法
- 可能遗漏某些调用场景
- 代码耦合度高

**实现位置**:
在 `AgentService.doChat()` 方法的 `agent.streamEvents()` 完成后,监听 end 事件并提取 usage 信息。

### 方案 C: 自定义 ModelFactory 包装器 (折中方案)

**优点**:
- 不侵入 AgentService
- 集中处理模型调用
- 相对容易实现

**缺点**:
- 需要修改 `ModelFactory.createModel()`
- 可能需要返回包装后的 Model 对象

**实现思路**:
```java
public Model createModel(ModelProviderConfig config) {
    Model originalModel = buildOriginalModel(config);
    return new TokenTrackingModelWrapper(originalModel, tokenUsageService);
}

class TokenTrackingModelWrapper implements Model {
    private final Model delegate;
    private final TokenUsageService tokenUsageService;
    
    @Override
    public Mono<ModelResponse> generate(...) {
        return delegate.generate(...)
            .doOnSuccess(response -> recordTokens(response));
    }
    
    private void recordTokens(ModelResponse response) {
        // 提取 usage 并调用 tokenUsageService.recordUsage()
    }
}
```

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

##  下一步工作

1. **完成自动 Token 记录**: 选择上述方案 A/B/C 之一实现
2. **添加成本计算**: 根据模型单价计算每次调用的费用 (CNY)
3. **设置额度预警**: 当用户 Token 消耗超过阈值时发送通知
4. **导出报表**: 支持 CSV/Excel 导出月度使用情况
5. **可视化优化**: 添加更多图表类型 (折线图、热力图等)

---

**最后更新**: 2026-08-27  
**维护者**: claw-agent 团队
