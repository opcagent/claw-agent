# Token 使用统计系统

## 📋 功能概述

为 claw-agent 平台实现了完整的 Token 使用追踪和统计功能,包括:

1. **Token 使用流水表** (`token_usage_log`): 记录每次模型调用的详细信息
2. **Token 使用汇总表** (`token_usage_summary`): 按用户+月份自动汇总统计
3. **REST API 接口**: 提供用户查询和管理员统计功能
4. **数据库触发器**: 自动维护汇总数据,无需手动计算

---

## 🗄️ 数据库设计

### 1. Token 使用流水表 (token_usage_log)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户ID |
| tenant_id | BIGINT | 租户ID |
| username | VARCHAR(100) | 用户名 |
| session_id | VARCHAR(100) | 会话ID |
| provider | VARCHAR(50) | 模型提供商 (openai/dashscope/ollama) |
| model_name | VARCHAR(100) | 模型名称 |
| prompt_tokens | INT | 提示词 Token 数 |
| completion_tokens | INT | 回复 Token 数 |
| total_tokens | INT | 总 Token 数 |
| request_id | VARCHAR(100) | 请求ID (用于追踪) |
| tool_name | VARCHAR(100) | 使用的工具名称 (可选) |
| usage_time | DATETIME | 使用时间 |
| usage_date | DATE | 使用日期 (计算字段) |

**索引**:
- `idx_user_date`: 用户 + 日期 (快速查询用户历史)
- `idx_tenant_date`: 租户 + 日期 (租户统计)
- `idx_provider`: 提供商 (按模型分析)
- `idx_session`: 会话ID (会话级统计)

### 2. Token 使用汇总表 (token_usage_summary)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户ID |
| tenant_id | BIGINT | 租户ID |
| username | VARCHAR(100) | 用户名 |
| period_type | VARCHAR(20) | 周期类型 (daily/monthly/yearly) |
| period_start | DATE | 周期开始日期 |
| period_end | DATE | 周期结束日期 |
| total_prompt_tokens | BIGINT | 累计提示词 Token |
| total_completion_tokens | BIGINT | 累计回复 Token |
| total_tokens | BIGINT | 累计总 Token |
| request_count | INT | 请求次数 |
| last_update_time | DATETIME | 最后更新时间 |

**唯一约束**: `uk_user_period` (user_id, period_type, period_start)

### 3. 自动更新触发器

```sql
CREATE TRIGGER trg_update_token_summary
AFTER INSERT ON token_usage_log
FOR EACH ROW
BEGIN
    -- 自动计算当月起止日期
    -- 插入或更新汇总表 (ON DUPLICATE KEY UPDATE)
    -- 累加 Token 计数和请求次数
END
```

**工作原理**:
- 每次插入流水记录时,触发器自动执行
- 计算当前月份的起止日期
- 如果该月汇总记录不存在,创建新记录
- 如果已存在,累加 Token 数量和请求次数

---

## 🔌 REST API 接口

### 基础路径: `/api/token-usage`

所有接口都需要 JWT 认证,从 token 中解析用户身份。

---

### 1. 查询当前用户本月 Token 使用汇总

**请求**:
```http
GET /api/token-usage/current-month
Authorization: Bearer <JWT_TOKEN>
```

**响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "userId": 1,
    "tenantId": 1,
    "username": "admin",
    "periodType": "monthly",
    "periodStart": "2026-08-01",
    "periodEnd": "2026-08-31",
    "totalPromptTokens": 15000,
    "totalCompletionTokens": 25000,
    "totalTokens": 40000,
    "requestCount": 120,
    "lastUpdateTime": "2026-08-27T12:00:00"
  }
}
```

**说明**: 如果没有本月数据,返回 `null`。

---

### 2. 查询指定月份的 Token 使用汇总

**请求**:
```http
GET /api/token-usage/month/{year}/{month}
Authorization: Bearer <JWT_TOKEN>
```

**示例**:
```http
GET /api/token-usage/month/2026/8
```

**响应**: 同上

---

### 3. 查询最近 N 个月的 Token 使用汇总列表

**请求**:
```http
GET /api/token-usage/recent-months?months=6
Authorization: Bearer <JWT_TOKEN>
```

**参数**:
- `months`: 月数,默认 6

**响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "periodStart": "2026-08-01",
      "totalTokens": 40000,
      "requestCount": 120
    },
    {
      "periodStart": "2026-07-01",
      "totalTokens": 35000,
      "requestCount": 100
    }
    // ... 更多月份
  ]
}
```

---

### 4. 查询 Token 使用流水 (最近 N 条)

**请求**:
```http
GET /api/token-usage/logs?limit=50
Authorization: Bearer <JWT_TOKEN>
```

**参数**:
- `limit`: 限制条数,默认 50

**响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 123,
      "userId": 1,
      "sessionId": "sess_abc123",
      "provider": "openai",
      "modelName": "gpt-4",
      "promptTokens": 150,
      "completionTokens": 250,
      "totalTokens": 400,
      "toolName": "web_search",
      "usageTime": "2026-08-27T12:30:00"
    }
    // ... 更多记录
  ]
}
```

---

### 5. 管理员查询租户下所有用户本月 Token 使用汇总

**请求**:
```http
GET /api/token-usage/admin/tenant-users?year=2026&month=8
Authorization: Bearer <JWT_TOKEN>
```

**参数**:
- `year`: 年份 (可选,默认当前年)
- `month`: 月份 (可选,默认当前月)

**权限**: ⚠️ **需要 ADMIN 角色** (TODO: 添加权限检查)

**响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "userId": 1,
      "username": "admin",
      "totalTokens": 40000,
      "requestCount": 120
    },
    {
      "userId": 2,
      "username": "user1",
      "totalTokens": 25000,
      "requestCount": 80
    }
  ]
}
```

**排序**: 按 Token 使用量降序排列

---

## 💻 代码结构

### 实体类 (Model)

- [TokenUsageLog.java](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/model/TokenUsageLog.java) - Token 使用流水实体
- [TokenUsageSummary.java](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/model/TokenUsageSummary.java) - Token 使用汇总实体

### Mapper

- [TokenUsageLogMapper.java](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/mapper/TokenUsageLogMapper.java) - 流水表 Mapper
- [TokenUsageSummaryMapper.java](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/mapper/TokenUsageSummaryMapper.java) - 汇总表 Mapper

### Service

- [TokenUsageService.java](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/service/TokenUsageService.java) - Token 使用统计服务

**核心方法**:
```java
// 记录一次模型调用的 Token 消耗
void recordUsage(Long userId, Long tenantId, String username, 
                String sessionId, String provider, String modelName,
                int promptTokens, int completionTokens,
                String requestId, String toolName)

// 查询当前月份汇总
TokenUsageSummary getCurrentMonthSummary(Long userId, Long tenantId)

// 查询指定月份汇总
TokenUsageSummary getMonthSummary(Long userId, Long tenantId, int year, int month)

// 查询最近 N 个月汇总列表
List<TokenUsageSummary> getRecentMonthsSummary(Long userId, Long tenantId, int months)

// 查询流水记录
List<TokenUsageLog> getUsageLogs(Long userId, Long tenantId, int limit)

// 查询租户下所有用户汇总 (管理员)
List<TokenUsageSummary> getTenantUsersSummary(Long tenantId, int year, int month)
```

### Controller

- [TokenUsageController.java](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/controller/TokenUsageController.java) - REST API 控制器

---

## 🔧 集成到模型调用

### 方案 1: 在 AgentService 中拦截模型响应

修改 [AgentService.java](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/service/AgentService.java),在流式响应结束后提取 Token 使用信息:

```java
// 伪代码示例
public Flux<ServerSentEvent<String>> chat(ChatRequest request, LoginUser user) {
    return agent.call(...)
        .doOnNext(event -> {
            // 流式输出
        })
        .doOnComplete(() -> {
            // 从 AgentState 中提取最后一次模型调用的 Token 使用
            UsageMetadata usage = extractUsageFromState(agent, ctx);
            if (usage != null) {
                tokenUsageService.recordUsage(
                    user.getUserId(),
                    user.getTenantId(),
                    user.getUsername(),
                    request.getSessionId(),
                    "openai", // 从配置获取
                    "gpt-4",   // 从配置获取
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    UUID.randomUUID().toString(),
                    null
                );
            }
        });
}
```

### 方案 2: 使用 AgentScope Middleware

创建自定义 Middleware,在模型调用后自动记录:

```java
@Component
public class TokenUsageMiddleware implements Middleware {
    
    @Autowired
    private TokenUsageService tokenUsageService;
    
    @Override
    public Mono<Msg> call(Msg msg, Chain chain, RuntimeContext ctx) {
        return chain.call(msg)
            .doOnSuccess(response -> {
                UsageMetadata usage = response.getUsage();
                if (usage != null) {
                    tokenUsageService.recordUsage(...);
                }
            });
    }
}
```

### 方案 3: 在 ModelFactory 中包装 Model

在创建 Model 时添加 Token 记录逻辑:

```java
private Model wrapModelWithTokenTracking(Model originalModel, LoginUser user) {
    return new Model() {
        @Override
        public Mono<ChatResponse> call(ChatRequest request) {
            return originalModel.call(request)
                .doOnSuccess(response -> {
                    UsageMetadata usage = response.getUsage();
                    tokenUsageService.recordUsage(...);
                });
        }
    };
}
```

---

## 📊 前端展示建议

### 1. 用户个人中心 - Token 使用面板

```html
<div class="token-usage-panel">
  <h3>本月 Token 使用情况</h3>
  <div class="stats">
    <div class="stat-item">
      <span class="label">总 Token:</span>
      <span class="value">{{ currentMonth.totalTokens }}</span>
    </div>
    <div class="stat-item">
      <span class="label">请求次数:</span>
      <span class="value">{{ currentMonth.requestCount }}</span>
    </div>
  </div>
  
  <!-- 最近 6 个月趋势图 -->
  <canvas id="token-trend-chart"></canvas>
</div>
```

### 2. 管理员后台 - 租户用户排行

```html
<table class="user-ranking-table">
  <thead>
    <tr>
      <th>用户名</th>
      <th>本月 Token</th>
      <th>请求次数</th>
      <th>占比</th>
    </tr>
  </thead>
  <tbody>
    <tr v-for="user in users" :key="user.userId">
      <td>{{ user.username }}</td>
      <td>{{ user.totalTokens }}</td>
      <td>{{ user.requestCount }}</td>
      <td>{{ (user.totalTokens / total * 100).toFixed(2) }}%</td>
    </tr>
  </tbody>
</table>
```

---

## ⚠️ 注意事项

### 1. 性能考虑

- **触发器开销**: 每次插入流水都会触发汇总更新,高并发场景下可能成为瓶颈
- **优化方案**: 
  - 异步记录 Token 使用 (不阻塞主流程)
  - 批量汇总 (每小时/每天定时任务)
  - 读写分离 (汇总表用于查询,流水表用于审计)

### 2. 数据一致性

- 触发器保证流水和汇总的强一致性
- 如果记录失败,不影响主业务流程 (catch 异常并记日志)

### 3. 存储容量

- 流水表会持续增长,建议定期归档或删除旧数据
- 汇总表长期保留,占用空间小

**清理策略示例**:
```sql
-- 删除 6 个月前的流水记录
DELETE FROM token_usage_log 
WHERE usage_time < DATE_SUB(NOW(), INTERVAL 6 MONTH);
```

### 4. 权限控制

- 管理员接口 (`/admin/tenant-users`) 需要添加角色校验
- 普通用户只能查看自己的数据

---

## 🚀 后续扩展

### 1. 额度管理

基于 Token 统计数据实现会员额度系统:

```java
// 检查用户是否超出配额
long usedTokens = getCurrentMonthSummary(userId).getTotalTokens();
long quota = getUserQuota(userId); // 从会员套餐获取

if (usedTokens >= quota) {
    throw new BizException(ResultCode.TOKEN_QUOTA_EXCEEDED);
}
```

### 2. 计费系统

根据 Token 使用量自动扣费:

```sql
-- 计费规则表
CREATE TABLE billing_rule (
    id BIGINT PRIMARY KEY,
    provider VARCHAR(50),
    model_name VARCHAR(100),
    prompt_price_per_1k DECIMAL(10, 6),  -- 每 1K prompt tokens 价格
    completion_price_per_1k DECIMAL(10, 6)
);

-- 用户余额表
CREATE TABLE user_balance (
    user_id BIGINT PRIMARY KEY,
    balance DECIMAL(10, 2),
    update_time DATETIME
);
```

### 3. 报表分析

- 按提供商分析成本分布
- 按工具分析使用频率
- 按时间段分析高峰时段
- 预测未来用量趋势

### 4. 告警通知

- 用户用量达到 80% 阈值时发送提醒
- 异常用量检测 (突然激增)
- 每日/每周用量报告邮件

---

## 📝 测试验证

### 1. 启动后端服务

```bash
cd d:\claw-agent\backend
mvn clean package -DskipTests
java -jar target/backend-1.0.0-SNAPSHOT.jar
```

### 2. 执行 Flyway 迁移

Flyway 会自动执行 `V32__token_usage_tracking.sql`,创建表和触发器。

### 3. 测试 API

```bash
# 登录获取 token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 查询本月 Token 使用
curl http://localhost:8080/api/token-usage/current-month \
  -H "Authorization: Bearer <TOKEN>"

# 查询流水
curl "http://localhost:8080/api/token-usage/logs?limit=10" \
  -H "Authorization: Bearer <TOKEN>"
```

### 4. 验证数据库

```sql
-- 查看流水记录
SELECT * FROM token_usage_log ORDER BY usage_time DESC LIMIT 10;

-- 查看汇总记录
SELECT * FROM token_usage_summary WHERE period_type = 'monthly';

-- 手动插入测试数据
INSERT INTO token_usage_log 
(user_id, tenant_id, username, provider, model_name, prompt_tokens, completion_tokens, total_tokens, usage_time)
VALUES 
(1, 1, 'admin', 'openai', 'gpt-4', 100, 200, 300, NOW());

-- 验证触发器是否自动更新了汇总表
SELECT * FROM token_usage_summary WHERE user_id = 1 AND period_type = 'monthly';
```

---

## 🎯 总结

✅ **已完成**:
- 数据库表设计 (流水表 + 汇总表)
- 自动更新触发器
- 实体类、Mapper、Service、Controller
- REST API 接口 (5个)
- 编译通过

⏳ **待完成**:
- 集成到模型调用流程 (方案 1/2/3 选其一)
- 前端页面开发
- 管理员权限校验
- 单元测试

💡 **核心价值**:
- 完整的 Token 使用追踪能力
- 实时统计分析
- 为会员额度和计费系统奠定基础
- 支持多维度数据分析
