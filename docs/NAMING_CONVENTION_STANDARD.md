# 命名规范统一标准

> **生效日期**: 2026-08-27  
> **适用范围**: 前后端所有代码  
> **强制级别**: ⚠️ 必须遵守

---

## 📋 核心原则

### 1. REST API 路径规范 (后端)

**规则**: 所有 REST API 路径使用 **小写驼峰 (lowerCamelCase)**,禁止使用连字符 `-`。

| ❌ 错误示例 | ✅ 正确示例 | 说明 |
|-----------|-----------|------|
| `/api/token-usage/current-month` | `/api/tokenUsage/currentMonth` | 模块名和方法名都用驼峰 |
| `/api/email-config/list` | `/api/emailConfig/list` | 类名转路径时保持驼峰 |
| `/api/admin/user-list` | `/api/admin/userList` | 方法名用驼峰 |
| `/api/chat/stream-sse` | `/api/chat/streamSse` | SSE 缩写保持大写首字母 |

**例外情况**:
- 路径参数保持原样: `/api/user/{userId}` → ✅
- 查询参数保持原样: `?pageSize=10&currentPage=1` → ✅

### 2. 前端 API 调用规范

**规则**: 前端调用后端 API 时,路径必须与后端保持一致 (小写驼峰)。

```typescript
// ❌ 错误
api.get("/api/token-usage/current-month")
api.post("/api/email-config/save")

// ✅ 正确
api.get("/api/tokenUsage/currentMonth")
api.post("/api/emailConfig/save")
```

### 3. Java 类与方法命名

**规则**: 遵循 Java 官方规范

| 类型 | 规范 | 示例 |
|-----|------|------|
| 类名 | UpperCamelCase | `EmailConfigController`, `TokenUsageService` |
| 方法名 | lowerCamelCase | `getCurrentMonth()`, `saveConfig()` |
| 变量名 | lowerCamelCase | `userId`, `smtpHost` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT` |
| 包名 | 全小写 | `com.claw.agent.controller` |

### 4. 数据库表与字段

**规则**: 使用 **小写下划线 (snake_case)**

| 类型 | 规范 | 示例 |
|-----|------|------|
| 表名 | snake_case | `email_config`, `token_usage_log` |
| 字段名 | snake_case | `user_id`, `create_time`, `smtp_host` |
| 索引名 | 前缀 + snake_case | `uk_user_email`, `idx_tenant_id` |

### 5. TypeScript/JavaScript 命名

**规则**: 遵循 JavaScript 官方规范

| 类型 | 规范 | 示例 |
|-----|------|------|
| 组件名 | PascalCase | `AppShell`, `SubagentCard` |
| 函数名 | camelCase | `loadTokenUsageData`, `handleSubmit` |
| 变量名 | camelCase | `sidebarCollapsed`, `isLoading` |
| 常量 | UPPER_SNAKE_CASE | `API_BASE_URL`, `MAX_FILE_SIZE` |
| 文件名 | kebab-case | `app-shell.tsx`, `subagent-card.tsx` |

### 6. AgentScope @Tool 工具命名

**规则**: 
- ✅ **必须使用 `@ToolSet` 注解对工具集进行分类**
- 工具方法名使用 `lowerCamelCase`
- 工具描述清晰简洁
- 分类代码必须从预定义列表中选择

```java
// ✅ 正确方式 (必须添加 @ToolSet 注解)
@Slf4j
@ToolSet(
    code = "email_tools",
    name = "邮件工具",
    description = "提供 SMTP 邮件发送、配置管理等功能",
    category = "utility",
    enabledByDefault = true,
    version = "1.0.0"
)
public class EmailTools {
    
    @Tool(name = "sendEmail", description = "发送邮件")
    public String sendEmail(...) { ... }
    
    @Tool(name = "listEmailConfigs", description = "列出邮箱配置")
    public String listEmailConfigs() { ... }
}

// ❌ 错误方式 (缺少 @ToolSet 注解)
@Slf4j
public class EmailTools { ... }
```

**预定义分类**:
| 分类代码 | 中文名称 | 说明 | 示例 |
|---------|---------|------|------|
| utility | 实用工具 | 时间、计算、编码等通用功能 | system_tools, math_tools, email_tools |
| search | 搜索工具 | 联网搜索、知识库检索 | multi_search |
| data | 数据处理 | 文件读写、数据转换 | (待扩展) |
| code | 代码相关 | 语法检查、正则测试 | (待扩展) |
| ai | AI 增强 | 文本生成、图像识别 | (待扩展) |
| system | 系统管理 | 配置查询、日志查看 | (待扩展) |

**注册方式**:
```java
// AgentRegistry.java
if (enabledToolCodes.contains("email")) {
    toolkit.registerTool(new EmailTools(emailService));
}
```

---

## 🔧 待调整清单

### 后端 Controller 路径调整

#### 1. TokenUsageController.java
**文件**: `backend/src/main/java/com/claw/agent/controller/TokenUsageController.java`

| 当前路径 | 调整后路径 | 状态 |
|---------|----------|------|
| `@RequestMapping("/api/tokenUsage")` | ✅ 已是驼峰 | 无需调整 |
| `@GetMapping("/currentMonth")` | ✅ 已是驼峰 | 无需调整 |
| `@GetMapping("/recentMonths")` | ✅ 已是驼峰 | 无需调整 |
| `@GetMapping("/admin/tenantUsers")` | ✅ 已是驼峰 | 无需调整 |

**结论**: TokenUsageController 已符合规范 ✅

#### 2. EmailConfigController.java
**文件**: `backend/src/main/java/com/claw/agent/controller/EmailConfigController.java`

| 当前路径 | 调整后路径 | 状态 |
|---------|----------|------|
| `@RequestMapping("/api/emailConfig")` | ✅ 已是驼峰 | 无需调整 |
| `@GetMapping("/list")` | ✅ 单段路径 | 无需调整 |
| `@PostMapping("/save")` | ✅ 单段路径 | 无需调整 |
| `@DeleteMapping("/delete/{id}")` | ✅ 单段路径 | 无需调整 |

**结论**: EmailConfigController 已符合规范 ✅

#### 3. 其他 Controller 检查

| Controller | RequestMapping | 状态 |
|-----------|---------------|------|
| AuthController | `/api/auth` | ✅ 单段,无需调整 |
| ChatController | `/api/chat` | ✅ 单段,无需调整 |
| UploadController | `/api/upload` | ✅ 单段,无需调整 |
| ConfigController | `/api/config` | ✅ 单段,无需调整 |
| PresetController | `/api/presets` | ✅ 单段,无需调整 |
| PipelineController | `/api/pipelines` | ✅ 单段,无需调整 |
| CapabilityController | `/api/capability` | ✅ 单段,无需调整 |
| ToolController | `/api/tools` | ✅ 单段,无需调整 |
| UserController | `/api/admin/user` | ⚠️ 需调整为 `/api/adminUser` |
| MenuController | `/api/admin/menu` | ⚠️ 需调整为 `/api/adminMenu` |
| RoleController | `/api/admin/role` | ⚠️ 需调整为 `/api/adminRole` |
| DeptController | `/api/admin/dept` | ⚠️ 需调整为 `/api/adminDept` |
| DictController | `/api/dict` | ✅ 单段,无需调整 |
| TenantController | `/api/admin/tenant` | ⚠️ 需调整为 `/api/adminTenant` |
| LogController | `/api/admin/log` | ⚠️ 需调整为 `/api/adminLog` |
| MonitorController | `/api/admin/online` | ⚠️ 需调整为 `/api/adminOnline` |

**需要调整的 Controller**:
1. UserController: `/api/admin/user` → `/api/adminUser`
2. MenuController: `/api/admin/menu` → `/api/adminMenu`
3. RoleController: `/api/admin/role` → `/api/adminRole`
4. DeptController: `/api/admin/dept` → `/api/adminDept`
5. TenantController: `/api/admin/tenant` → `/api/adminTenant`
6. LogController: `/api/admin/log` → `/api/adminLog`
7. MonitorController: `/api/admin/online` → `/api/adminOnline`

### 前端 API 调用调整

需要全局搜索并替换以下模式:

```typescript
// 搜索模式: /api/admin/[a-z]+
// 替换为: /api/admin[首字母大写]

/api/admin/user → /api/adminUser
/api/admin/menu → /api/adminMenu
/api/admin/role → /api/adminRole
/api/admin/dept → /api/adminDept
/api/admin/tenant → /api/adminTenant
/api/admin/log → /api/adminLog
/api/admin/online → /api/adminOnline
```

---

## 📝 实施步骤

### Phase 1: 后端调整 (优先级 P0)

1. **修改 Controller @RequestMapping**
   ```bash
   # 批量替换
   sed -i 's|@RequestMapping("/api/admin/user")|@RequestMapping("/api/adminUser")|g' UserController.java
   sed -i 's|@RequestMapping("/api/admin/menu")|@RequestMapping("/api/adminMenu")|g' MenuController.java
   # ... 其他 Controller
   ```

2. **更新相关方法路径**
   - 检查所有 `@GetMapping`, `@PostMapping` 等方法
   - 确保路径片段也是驼峰命名

3. **编译验证**
   ```bash
   cd backend
   mvn clean compile -DskipTests
   ```

### Phase 2: 前端调整 (优先级 P0)

1. **全局搜索替换**
   ```bash
   # 在 frontend/src 目录下
   find . -name "*.ts" -o -name "*.tsx" | xargs grep -l "/api/admin/"
   
   # 批量替换 (谨慎操作,建议逐个文件确认)
   sed -i 's|/api/admin/user|/api/adminUser|g' *.tsx
   sed -i 's|/api/admin/menu|/api/adminMenu|g' *.tsx
   # ... 其他路径
   ```

2. **TypeScript 类型检查**
   ```bash
   cd frontend
   npm run build
   ```

### Phase 3: 文档更新 (优先级 P1)

1. **更新 API 文档**
   - 修改 `docs/API_REFERENCE.md`
   - 更新所有接口路径示例

2. **更新开发规范**
   - 本文档作为标准纳入 AGENTS.md
   - 添加到项目 README

### Phase 4: 测试验证 (优先级 P0)

1. **后端单元测试**
   ```bash
   cd backend
   mvn test
   ```

2. **前端 E2E 测试**
   ```bash
   cd frontend
   npm run test:e2e
   ```

3. **手动测试关键流程**
   - 登录/登出
   - 用户管理 CRUD
   - 菜单权限配置
   - 角色管理
   - 部门管理
   - 租户管理

---

## ⚠️ 注意事项

### 1. 向后兼容性

**问题**: 修改 API 路径会破坏现有客户端调用。

**解决方案**:
- **方案 A** (推荐): 同时保留旧路径,添加 `@Deprecated` 标记
  ```java
  @GetMapping("/admin/user/list")  // 旧路径,标记废弃
  @Deprecated(since = "2026-08-27", forRemoval = true)
  public Result<List<User>> listOld() { ... }
  
  @GetMapping("/adminUser/list")  // 新路径
  public Result<List<User>> list() { ... }
  ```

- **方案 B**: 一次性切换,通知所有客户端更新
  - 适用于内部系统,外部依赖少

### 2. 前端硬编码路径

**风险**: 前端可能在多处硬编码 API 路径。

**解决方案**:
- 集中管理 API 路径常量
  ```typescript
  // lib/api-paths.ts
  export const API_PATHS = {
    ADMIN_USER_LIST: '/api/adminUser/list',
    ADMIN_MENU_TREE: '/api/adminMenu/tree',
    // ...
  };
  
  // 使用时
  api.get(API_PATHS.ADMIN_USER_LIST)
  ```

### 3. 第三方集成

**检查项**:
- 移动端 App 是否调用这些 API
- 外部系统集成文档是否需要更新
- Webhook 回调地址是否受影响

---

## 🎯 最终目标

### 统一的命名风格

| 层级 | 命名风格 | 示例 |
|-----|---------|------|
| **REST API 路径** | lowerCamelCase | `/api/adminUser/listRoles` |
| **Java 类名** | UpperCamelCase | `AdminUserController` |
| **Java 方法名** | lowerCamelCase | `listRoles()` |
| **数据库表** | snake_case | `sys_user_role` |
| **数据库字段** | snake_case | `user_id`, `role_name` |
| **TS 组件** | PascalCase | `UserRoleSelector` |
| **TS 函数** | camelCase | `fetchUserRoles()` |
| **TS 变量** | camelCase | `isLoading`, `userList` |
| **Agent Tool** | lowerCamelCase | `sendEmail()`, `queryDatabase()` |

### 工具集必须使用 @ToolSet 分类

**原因**:
1. **平台感知**: `ToolRegistry` 通过扫描 `@ToolSet` 注解自动发现可用工具集
2. **前端展示**: 前端通过 `/api/tools/list` API 获取工具列表并展示分类
3. **细粒度控制**: 支持按分类筛选、启用/禁用工具集
4. **依赖管理**: `@ToolSet` 的 `dependencies` 字段声明工具集间依赖关系
5. **版本管理**: `version` 字段支持工具集版本追踪

**强制要求**:
```java
// ✅ 必须添加 @ToolSet 注解
@Slf4j
@ToolSet(
    code = "email_tools",      // 唯一标识符 (小写+下划线)
    name = "邮件工具",         // 中文显示名称
    description = "提供 SMTP 邮件发送功能",  // 简短描述
    category = "utility",      // 分类代码 (从预定义列表选择)
    enabledByDefault = true,   // 默认启用状态
    version = "1.0.0"          // 语义化版本号
)
public class EmailTools {
    @Tool(name = "sendEmail", description = "发送邮件")
    public String sendEmail(...) { ... }
}

// ❌ 禁止缺少 @ToolSet 注解
@Slf4j
public class EmailTools { ... }  // 错误! 平台无法感知此工具集
```

**注意事项**:
- `code` 必须全局唯一,建议使用 `{功能}_{tools}` 格式
- `category` 必须在预定义列表中,新增分类需先更新枚举
- `description` 禁止使用 Markdown 或特殊字符
- 工具方法必须使用 `@Tool` 注解标记

---

## 📊 影响范围评估

### 后端文件 (预计修改 7 个 Controller)

| 文件 | 修改内容 | 影响方法数 |
|-----|---------|----------|
| UserController.java | RequestMapping | ~10 个方法 |
| MenuController.java | RequestMapping | ~8 个方法 |
| RoleController.java | RequestMapping | ~12 个方法 |
| DeptController.java | RequestMapping | ~6 个方法 |
| TenantController.java | RequestMapping | ~8 个方法 |
| LogController.java | RequestMapping | ~4 个方法 |
| MonitorController.java | RequestMapping | ~3 个方法 |

**总计**: ~51 个方法路径需要更新

### 前端文件 (预计修改 10+ 个页面)

| 文件 | 修改内容 | 影响行数 |
|-----|---------|---------|
| system/user/page.tsx | API 路径 | ~15 行 |
| system/menu/page.tsx | API 路径 | ~20 行 |
| system/role/page.tsx | API 路径 | ~25 行 |
| system/dept/page.tsx | API 路径 | ~12 行 |
| system/tenant/page.tsx | API 路径 | ~18 行 |
| system/config/page.tsx | API 路径 | ~30 行 |
| lib/api.ts | 可能需更新类型定义 | ~5 行 |

**总计**: ~125 行代码需要更新

---

## ✅ 验收标准

### 后端验收

- [ ] 所有 Controller 的 `@RequestMapping` 使用驼峰命名
- [ ] Maven 编译通过 (`mvn clean compile`)
- [ ] 单元测试全部通过 (`mvn test`)
- [ ] Swagger/OpenAPI 文档路径已更新
- [ ] 启动日志无警告

### 前端验收

- [ ] 所有 API 调用路径与后端一致
- [ ] TypeScript 编译通过 (`npm run build`)
- [ ] ESLint 无错误 (`npm run lint`)
- [ ] 关键业务流程测试通过
- [ ] 浏览器控制台无 404 错误

### 文档验收

- [ ] API 参考文档已更新
- [ ] 开发规范文档已纳入 AGENTS.md
- [ ] 迁移指南已编写 (如需向后兼容)

---

## 🔄 持续改进

### 自动化检查

**建议添加 Pre-commit Hook**:

```bash
#!/bin/bash
# .git/hooks/pre-commit

# 检查后端 API 路径是否包含连字符
if grep -r '@RequestMapping.*-"' backend/src/main/java/com/claw/agent/controller/; then
    echo "❌ 错误: REST API 路径不能使用连字符,请使用驼峰命名"
    exit 1
fi

# 检查前端 API 调用是否包含连字符
if grep -r 'api\.(get|post|put|delete).*"/api/.*-"' frontend/src/; then
    echo "❌ 错误: 前端 API 调用路径不能使用连字符,请使用驼峰命名"
    exit 1
fi

exit 0
```

### CI/CD 集成

**GitHub Actions 示例**:

```yaml
name: Naming Convention Check

on: [pull_request]

jobs:
  check-naming:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Check Backend API Paths
        run: |
          if grep -r '@RequestMapping.*-"' backend/src/; then
            echo "::error::Backend API paths must use camelCase"
            exit 1
          fi
      
      - name: Check Frontend API Calls
        run: |
          if grep -r 'api\..*".*/api/.*-"' frontend/src/; then
            echo "::error::Frontend API calls must use camelCase"
            exit 1
          fi
```

---

## 📚 参考资料

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Microsoft TypeScript Coding Guidelines](https://github.com/Microsoft/TypeScript/wiki/Coding-guidelines)
- [REST API Design Best Practices](https://restfulapi.net/rest-api-naming-conventions-and-best-practices/)
- [AgentScope Java Documentation](https://agentscope.io/)

---

**最后更新**: 2026-08-27  
**维护者**: claw-agent 开发团队  
**版本**: v1.0
