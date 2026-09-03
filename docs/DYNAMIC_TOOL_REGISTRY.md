# 动态工具注册系统

本文档说明 Claw Agent 的动态工具注册系统,解决"平台不知道有哪些 Tool"的问题。

## 🎯 问题背景

### 原有方案的问题

1. **硬编码注册** - 在 `AgentRegistry.java` 中手动 `registerTool(new XxxTools())`
2. **平台不知情** - 没有统一的工具发现机制,无法列出所有可用工具
3. **无法动态管理** - 启用/禁用工具需要修改代码并重新编译
4. **缺乏元数据** - 工具的描述、分类、版本等信息分散在各处

### 解决方案

引入**基于注解的自动扫描与注册机制**:

```
┌─────────────────────────────────────────────┐
│           Tool Discovery System             │
─────────────────────────────────────────────┤
│  1. @ToolSet 注解 (元数据声明)               │
│  2. ClassPath Scanning (启动时扫描)          │
│  3. ToolRegistry (运行时注册表)              │
│  4. REST API (动态管理接口)                  │
└─────────────────────────────────────────────┘
```

---

## 📦 核心组件

### 1. ToolSet 注解

**位置**: [`ToolSet.java`](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/tool/annotation/ToolSet.java)

**作用**: 标记工具类,提供元数据信息

**属性**:
- `code`: 工具集唯一标识符(如 `system_tools`)
- `name`: 显示名称(如 "系统工具")
- `description`: 描述信息
- `category`: 分类(`utility`/`search`/`data`/`code`/`ai`)
- `enabledByDefault`: 是否默认启用
- `version`: 版本号
- `dependencies`: 依赖的其他工具集
- `requiresHITL`: 是否需要人工审批
- `allowedRoles`: 允许使用的角色列表

**示例**:
```java
@ToolSet(
    code = "system_tools",
    name = "系统工具",
    description = "提供时间查询、日期计算、UUID 生成等基础功能",
    category = "utility",
    enabledByDefault = true,
    version = "1.0.0"
)
public class SystemTools {
    // ...
}
```

### 2. ToolRegistry 注册器

**位置**: [`ToolRegistry.java`](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/config/ToolRegistry.java)

**职责**:
- 启动时扫描 `com.claw.agent.tool` 包
- 提取 `@ToolSet` 注解元数据
- 维护已启用工具集列表
- 提供实例化工具的方法

**核心方法**:
```java
// 初始化工具注册表(启动时调用)
void initialize()

// 获取所有工具集元数据
List<ToolMetadata> getAllToolSets()

// 检查工具集是否已启用
boolean isToolSetEnabled(String code)

// 启用/禁用工具集
void enableToolSet(String code)
void disableToolSet(String code)

// 实例化工具对象
Object instantiateTool(String code)
```

### 3. ToolController 管理接口

**位置**: [`ToolController.java`](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/controller/ToolController.java)

**REST API**:

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/tools/list` | GET | 获取所有工具集列表 |
| `/api/tools/enabled` | GET | 获取已启用的工具集 |
| `/api/tools/category/{category}` | GET | 按分类获取工具集 |
| `/api/tools/{code}` | GET | 获取单个工具集详情 |
| `/api/tools/{code}/enable` | POST | 启用工具集 |
| `/api/tools/{code}/disable` | POST | 禁用工具集 |
| `/api/tools/batch-enable` | POST | 批量启用 |
| `/api/tools/batch-disable` | POST | 批量禁用 |

### 4. AgentRegistry 集成

**位置**: [`AgentRegistry.java`](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/config/AgentRegistry.java)

**变更**:
- 注入 `ToolRegistry`
- 从 `toolRegistry.getEnabledToolCodes()` 获取已启用工具集
- 遍历并实例化每个工具集
- 特殊处理需要参数的工具(如 `NoteTools`、`WebSearchTools`)

**代码片段**:
```java
// 获取已启用的工具集代码列表
Set<String> enabledToolCodes = toolRegistry.getEnabledToolCodes();

// 遍历并注册每个工具集
for (String toolCode : enabledToolCodes) {
    Object toolInstance = toolRegistry.instantiateTool(toolCode);
    toolkit.registerTool(toolInstance);
}
```

### 5. 应用启动初始化

**位置**: [`ClawAgentApplication.java`](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/ClawAgentApplication.java)

**Bean**:
```java
@Bean
public CommandLineRunner initToolRegistry(ToolRegistry toolRegistry) {
    return args -> {
        toolRegistry.initialize();
    };
}
```

---

## 🚀 使用流程

### 添加新工具集

#### 步骤 1: 创建工具类并添加注解

```java
package com.claw.agent.tool;

import com.claw.agent.tool.annotation.ToolSet;
import io.agentscope.core.tool.Tool;

@Slf4j
@ToolSet(
    code = "weather_tools",
    name = "天气工具",
    description = "查询天气预报和实时天气",
    category = "utility",
    enabledByDefault = false,
    version = "1.0.0"
)
public class WeatherTools {

    @Tool(name = "get_weather", description = "获取指定城市的天气")
    public String getWeather(String city) {
        // 实现逻辑
        return "天气信息";
    }
}
```

#### 步骤 2: 重启应用

应用启动时会自动扫描并注册新工具集。

#### 步骤 3: (可选) 启用工具集

如果 `enabledByDefault = false`,需要通过 API 或数据库配置启用:

```bash
curl -X POST http://localhost:8080/api/tools/weather_tools/enable
```

### 查询可用工具集

#### 获取所有工具集

```bash
curl http://localhost:8080/api/tools/list
```

**响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "code": "system_tools",
      "name": "系统工具",
      "description": "提供时间查询、日期计算、UUID 生成等基础功能",
      "category": "utility",
      "enabledByDefault": true,
      "version": "1.0.0",
      "dependencies": [],
      "requiresHITL": false,
      "allowedRoles": []
    },
    {
      "code": "math_tools",
      "name": "数学工具",
      "description": "提供数学计算、哈希函数、Base64 编解码等功能",
      "category": "utility",
      "enabledByDefault": true,
      "version": "1.0.0",
      "dependencies": [],
      "requiresHITL": false,
      "allowedRoles": []
    }
  ]
}
```

#### 获取已启用的工具集

```bash
curl http://localhost:8080/api/tools/enabled
```

**响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": ["system_tools", "math_tools", "web_search"]
}
```

#### 按分类获取

```bash
curl http://localhost:8080/api/tools/category/utility
```

### 启用/禁用工具集

#### 启用单个工具集

```bash
curl -X POST http://localhost:8080/api/tools/weather_tools/enable
```

#### 禁用单个工具集

```bash
curl -X POST http://localhost:8080/api/tools/weather_tools/disable
```

#### 批量启用

```bash
curl -X POST http://localhost:8080/api/tools/batch-enable \
  -H "Content-Type: application/json" \
  -d '["weather_tools", "code_tools"]'
```

---

## 📊 架构优势

### 1. 自动化发现 ✅

- **零配置**: 只需添加 `@ToolSet` 注解,无需手动注册
- **热插拔**: 新增工具集无需修改 `AgentRegistry`
- **可扩展**: 支持任意数量的工具集

### 2. 平台感知 ✅

- **统一视图**: `/api/tools/list` 返回所有可用工具集
- **元数据完整**: 包含描述、分类、版本、依赖等信息
- **前端友好**: 可直接用于管理后台展示

### 3. 动态管理 ✅

- **运行时控制**: 通过 API 启用/禁用工具集
- **依赖检查**: 禁用时检查是否有其他工具集依赖
- **批量操作**: 支持批量启用/禁用

### 4. 权限集成 ✅

- **角色限制**: `allowedRoles` 定义适用角色
- **HITL 支持**: `requiresHITL` 标记需要审批的工具
- **细粒度控制**: 可与现有权限系统无缝集成

### 5. 版本管理 ✅

- **语义化版本**: 支持 `1.0.0`、`2.1.3` 等格式
- **兼容性提示**: 可根据版本号提示用户更新
- **向后兼容**: 旧版本工具集可共存

---

## 🔧 技术细节

### 扫描机制

使用 Spring 的 `ClassPathScanningCandidateComponentProvider`:

```java
ClassPathScanningCandidateComponentProvider scanner = 
    new ClassPathScanningCandidateComponentProvider(false);
scanner.addIncludeFilter(new AnnotationTypeFilter(ToolSet.class));

Set<BeanDefinition> candidates = 
    scanner.findCandidateComponents("com.claw.agent.tool");
```

### 实例化策略

通过反射无参构造函数实例化:

```java
public Object instantiateTool(String code) {
    ToolMetadata metadata = toolMetadataCache.get(code);
    return metadata.getToolClass().getDeclaredConstructor().newInstance();
}
```

**注意**: 需要特殊处理的工具(如 `NoteTools`、`MultiSearchTools`)通过 `BuiltinToolFactory` 工厂模式实例化，在 `AgentRegistry` 中统一处理。

### 依赖管理

启用工具集时检查依赖:

```java
public void enableToolSet(String code) {
    ToolMetadata metadata = toolMetadataCache.get(code);
    
    // 检查依赖
    for (String dep : metadata.getDependencies()) {
        if (!enabledToolCodes.contains(dep)) {
            throw new IllegalStateException(
                String.format("工具集 %s 依赖的工具集 %s 未启用", code, dep));
        }
    }
    
    enabledToolCodes.add(code);
}
```

禁用时检查反向依赖:

```java
public void disableToolSet(String code) {
    // 检查是否有其他工具集依赖此工具集
    for (Map.Entry<String, ToolMetadata> entry : toolMetadataCache.entrySet()) {
        if (entry.getValue().getDependencies().contains(code) && 
            enabledToolCodes.contains(entry.getKey())) {
            throw new IllegalStateException(
                String.format("工具集 %s 被 %s 依赖，无法禁用", code, entry.getKey()));
        }
    }
    
    enabledToolCodes.remove(code);
}
```

---

## 📝 最佳实践

### 1. 命名规范

- **code**: 小写字母+下划线,如 `system_tools`、`weather_api`
- **category**: 使用预定义值:`utility`、`search`、`data`、`code`、`ai`
- **version**: 遵循语义化版本,如 `1.0.0`、`2.1.3-beta`

### 2. 默认启用策略

- **核心工具**: `enabledByDefault = true`(如系统工具、数学工具)
- **外部依赖**: `enabledByDefault = false`(如需要 API Key 的天气工具)
- **实验性工具**: `enabledByDefault = false`(需测试后启用)

### 3. 依赖声明

明确声明工具集之间的依赖关系:

```java
@ToolSet(
    code = "advanced_math",
    dependencies = {"math_tools"}  // 依赖基础数学工具
)
```

### 4. HITL 审批

对危险操作标记需要人工审批:

```java
@ToolSet(
    code = "file_operations",
    requiresHITL = true  // 文件删除等操作需要审批
)
```

### 5. 角色限制

限制特定工具集的访问权限:

```java
@ToolSet(
    code = "admin_tools",
    allowedRoles = {"ADMIN"}  // 仅管理员可用
)
```

---

##  常见问题

### Q1: 新添加的工具集没有被扫描到?

**A**: 确保:
1. 类在 `com.claw.agent.tool` 包或其子包下
2. 添加了 `@ToolSet` 注解
3. 重启了应用(触发 `CommandLineRunner`)

### Q2: 工具集启用失败,提示依赖未启用?

**A**: 先启用依赖的工具集:

```bash
# 先启用依赖
curl -X POST http://localhost:8080/api/tools/math_tools/enable

# 再启用目标工具集
curl -X POST http://localhost:8080/api/tools/advanced_math/enable
```

### Q3: 如何查看某个工具集包含哪些具体工具?

**A**: 当前版本只管理工具集级别,具体工具由 AgentScope 的 `@Tool` 注解管理。可通过以下方式查看:

1. 查看工具集源码中的 `@Tool` 方法
2. 未来可扩展:在 `ToolMetadata` 中添加 `tools` 字段,自动提取 `@Tool` 注解

### Q4: 工具集禁用后,正在运行的会话会怎样?

**A**: 
- **新建会话**: 不会加载已禁用的工具集
- **已有会话**: 不受影响,继续使用已加载的工具
- **建议**: 禁用前通知用户,避免突然失效

---

##  未来计划

### 短期（1-2周）✅ 已完成
- [x] 前端管理界面（工具集列表、启用/禁用开关）
- [x] 工具集详情页（`/api/tools/list-with-details` 展示包含的具体工具）
- [ ] 工具使用统计（调用次数、成功率）

### 中期(1个月)
- [ ] 工具集市场(第三方工具集发布与安装)
- [ ] 版本升级提示(检测到新版本时提醒)
- [ ] 工具集模板(快速创建新工具集)

### 长期(2-3个月)
- [ ] 工具集依赖图可视化
- [ ] 自动冲突检测(同名工具、功能重叠)
- [ ] 沙箱隔离(不同工具集运行环境隔离)

---

## 📞 技术支持

如有问题或建议:

1. **提交 Issue**: GitHub Issues
2. **查看文档**: [TOOLS_REFERENCE.md](./TOOLS_REFERENCE.md)
3. **联系开发**: dev@claw-agent.com

---

## 📝 更新日志

### v2.0.0 (2026-08-26)
- ✅ 新增 `@ToolSet` 注解
- ✅ 新增 `ToolRegistry` 自动扫描与注册
- ✅ 新增 `ToolController` REST API
- ✅ 集成到 `AgentRegistry` 动态加载工具
- ✅ 支持启用/禁用、依赖检查、批量操作

### v1.0.0 (2026-08-20)
-  硬编码工具注册(已废弃)
