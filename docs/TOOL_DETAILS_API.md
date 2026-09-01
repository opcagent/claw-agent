# 工具详情 API 文档

本文档说明如何让平台界面知道有哪些具体的 Tool(不仅仅是工具集)。

## 🎯 问题解决

### 之前的问题
- ✅ `/api/tools/list` 返回工具**集**列表(如 `system_tools`, `math_tools`)
- ❌ 但**不知道每个工具集里有哪些具体工具**(如 `get_current_time`, `calculate`)
- ❌ 前端无法展示完整的工具清单

### 现在的解决方案
新增多个 API,让平台能够查询和管理所有工具的详细信息:

**核心 API**:
1. `GET /api/tools/list-with-details` - 获取所有工具集及其包含的工具（含详情）
2. `GET /api/tools/{code}/tools` - 获取指定工具集的所有工具详情
3. `GET /api/tools/list` - 获取所有工具集列表（不含工具详情）
4. `GET /api/tools/enabled` - 获取已启用的工具集代码列表
5. `GET /api/tools/category/{category}` - 按分类获取工具集
6. `GET /api/tools/{code}` - 获取单个工具集详情
7. `POST /api/tools/{code}/enable` - 启用工具集
8. `POST /api/tools/{code}/disable` - 禁用工具集
9. `POST /api/tools/batch-enable` - 批量启用工具集
10. `POST /api/tools/batch-disable` - 批量禁用工具集

---

## 📡 API 参考

### 1. 获取所有工具集及工具详情（推荐）

**端点**: `GET /api/tools/list-with-details`

**说明**: 返回所有工具集的完整信息,包括每个工具集包含的具体工具列表。

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "code": "system_tools",
      "name": "系统工具",
      "description": "提供时间查询、日期计算、UUID 生成、系统信息等基础功能",
      "category": "utility",
      "enabledByDefault": true,
      "version": "1.0.0",
      "dependencies": [],
      "requiresHITL": false,
      "allowedRoles": [],
      "enabled": true,
      "tools": [
        {
          "name": "get_current_time",
          "description": "获取当前时间",
          "returnType": "String",
          "parameters": []
        },
        {
          "name": "calculate_date",
          "description": "日期计算",
          "returnType": "String",
          "parameters": [
            {
              "name": "days",
              "type": "int",
              "description": "",
              "required": true
            }
          ]
        }
      ]
    },
    {
      "code": "math_tools",
      "name": "数学工具",
      "description": "提供数学计算、哈希函数、Base64 编解码、单位换算、密码生成等功能",
      "category": "utility",
      "enabledByDefault": true,
      "version": "1.0.0",
      "dependencies": [],
      "requiresHITL": false,
      "allowedRoles": [],
      "enabled": true,
      "tools": [
        {
          "name": "calculate",
          "description": "执行数学表达式计算",
          "returnType": "String",
          "parameters": [
            {
              "name": "expression",
              "type": "String",
              "description": "",
              "required": true
            }
          ]
        }
      ]
    }
  ]
}
```

### 2. 获取指定工具集的工具详情

**端点**: `GET /api/tools/{code}/tools`

**说明**: 返回指定工具集包含的所有工具详情。

**请求示例**:
```bash
curl http://localhost:8080/api/tools/system_tools/tools
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "name": "get_current_time",
      "description": "获取当前时间",
      "returnType": "String",
      "parameters": []
    },
    {
      "name": "calculate_date",
      "description": "日期计算",
      "returnType": "String",
      "parameters": [
        {
          "name": "days",
          "type": "int",
          "description": "",
          "required": true
        }
      ]
    }
  ]
}
```

### 3. 获取所有工具集列表（不含详情）

**端点**: `GET /api/tools/list`

**说明**: 返回所有工具集的元数据，不包含具体工具列表。适用于快速查询工具集基本信息。

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "code": "system_tools",
      "name": "系统工具",
      "description": "提供时间查询、日期计算、UUID 生成、系统信息等基础功能",
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

### 4. 获取已启用的工具集列表

**端点**: `GET /api/tools/enabled`

**说明**: 返回当前已启用的工具集代码列表。

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": ["system_tools", "math_tools", "web_search"]
}
```

### 5. 按分类获取工具集

**端点**: `GET /api/tools/category/{category}`

**说明**: 根据分类筛选工具集。支持的分类: utility(实用工具)、search(搜索)、data(数据处理)、code(代码相关)、ai(AI 增强)、system(系统管理)。

**请求示例**:
```bash
curl http://localhost:8080/api/tools/category/utility
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "code": "system_tools",
      "name": "系统工具",
      "category": "utility"
    },
    {
      "code": "math_tools",
      "name": "数学工具",
      "category": "utility"
    }
  ]
}
```

### 6. 获取单个工具集详情

**端点**: `GET /api/tools/{code}`

**说明**: 获取指定工具集的元数据。

**请求示例**:
```bash
curl http://localhost:8080/api/tools/system_tools
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "code": "system_tools",
    "name": "系统工具",
    "description": "提供时间查询、日期计算、UUID 生成、系统信息等基础功能",
    "category": "utility",
    "enabledByDefault": true,
    "version": "1.0.0"
  }
}
```

### 7. 启用工具集

**端点**: `POST /api/tools/{code}/enable`

**说明**: 动态启用指定的工具集。启用后会检查依赖关系，如果依赖的工具集未启用则抛出异常。

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/tools/system_tools/enable
```

**成功响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

**失败响应** (依赖未满足):
```json
{
  "code": 500,
  "message": "工具集 system_tools 依赖的工具集 math_tools 未启用",
  "data": null
}
```

### 8. 禁用工具集

**端点**: `POST /api/tools/{code}/disable`

**说明**: 动态禁用指定的工具集。如果有其他已启用的工具集依赖此工具集，则禁止禁用。

**请求示例**:
```bash
curl -X POST http://localhost:8080/api/tools/system_tools/disable
```

**成功响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

**失败响应** (被其他工具集依赖):
```json
{
  "code": 500,
  "message": "工具集 math_tools 被 system_tools 依赖，无法禁用",
  "data": null
}
```

### 9. 批量启用工具集

**端点**: `POST /api/tools/batch-enable`

**说明**: 批量启用多个工具集。部分失败不会中断整个流程，会返回成功和失败的数量。

**请求体**:
```json
["system_tools", "math_tools", "web_search"]
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功启用 2 个，失败 1 个",
  "data": null
}
```

### 10. 批量禁用工具集

**端点**: `POST /api/tools/batch-disable`

**说明**: 批量禁用多个工具集。部分失败不会中断整个流程。

**请求体**:
```json
["system_tools", "math_tools"]
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功禁用 2 个，失败 0 个",
  "data": null
}
```

---

## 🔧 技术实现

### 核心组件

#### 1. ToolDetailExtractor (工具详情提取器)

**位置**: [`ToolDetailExtractor.java`](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/config/ToolDetailExtractor.java)

**职责**: 从工具类中提取 `@Tool` 注解的方法,生成工具详情列表。

**关键方法**:
```java
public static List<ToolDetail> extractTools(Class<?> toolClass) {
    List<ToolDetail> tools = new ArrayList<>();
    
    for (Method method : toolClass.getDeclaredMethods()) {
        Tool toolAnnotation = method.getAnnotation(Tool.class);
        if (toolAnnotation != null) {
            ToolDetail detail = new ToolDetail();
            detail.setName(toolAnnotation.name());
            detail.setDescription(toolAnnotation.description());
            detail.setReturnType(method.getReturnType().getSimpleName());
            
            // 提取参数信息
            List<ToolParameter> params = new ArrayList<>();
            Parameter[] parameters = method.getParameters();
            for (Parameter param : parameters) {
                ToolParameter tp = new ToolParameter();
                tp.setName(param.getName());
                tp.setType(param.getType().getSimpleName());
                tp.setRequired(true); // 默认必填
                params.add(tp);
            }
            detail.setParameters(params);
            
            tools.add(detail);
        }
    }
    
    return tools;
}
```

#### 2. ToolRegistry (工具注册表)

**位置**: [`ToolRegistry.java`](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/config/ToolRegistry.java)

**新增字段**:
```java
/** 工具详情缓存（code -> tools） */
private final Map<String, List<ToolDetailExtractor.ToolDetail>> toolDetailsCache = new HashMap<>();
```

**初始化时提取工具详情**:
```java
// 在 initialize() 方法中
try {
    List<ToolDetailExtractor.ToolDetail> tools = 
        ToolDetailExtractor.extractTools(clazz);
    toolDetailsCache.put(metadata.getCode(), tools);
    log.debug("  └─ 包含 {} 个工具: {}", tools.size(), 
        tools.stream().map(t -> t.getName()).collect(Collectors.joining(", ")));
} catch (Exception e) {
    log.warn("  └─ 提取工具详情失败: {}", e.getMessage());
    toolDetailsCache.put(metadata.getCode(), new ArrayList<>());
}
```

**新增方法**:
```java
/**
 * 获取指定工具集的工具详情列表。
 */
public List<ToolDetailExtractor.ToolDetail> getToolDetails(String code) {
    return toolDetailsCache.getOrDefault(code, new ArrayList<>());
}

/**
 * 获取所有工具集的完整信息（包含工具详情）。
 */
public List<ToolMetadataWithDetails> getAllToolSetsWithDetails() {
    return toolMetadataCache.values().stream()
            .map(metadata -> {
                ToolMetadataWithDetails withDetails = new ToolMetadataWithDetails();
                // ... 复制元数据字段
                withDetails.setTools(toolDetailsCache.getOrDefault(metadata.getCode(), new ArrayList<>()));
                return withDetails;
            })
            .collect(Collectors.toList());
}
```

#### 3. ToolFactory (工具工厂接口)

**位置**: [`ToolFactory.java`](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/config/ToolFactory.java) / [`BuiltinToolFactory.java`](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/config/BuiltinToolFactory.java)

**职责**: 为需要特殊参数的工具提供自定义实例化逻辑,解决统一注册与个性化初始化的矛盾。

**接口定义**:
```java
public interface ToolFactory<T> {
    /**
     * 创建工具实例。
     */
    T create(ToolRegistry.ToolMetadata metadata);
    
    /**
     * 是否支持指定的工具集。
     */
    boolean supports(String code);
}
```

**内置实现 - BuiltinToolFactory**:
```java
@Component
@RequiredArgsConstructor
public class BuiltinToolFactory implements ToolFactory<Object> {
    private final HttpProxyConfig proxyConfig;
    private final BingSearchConfig bingSearchConfig;

    @Override
    public Object create(ToolRegistry.ToolMetadata metadata) {
        String code = metadata.getCode();
        
        // WebSearchTools 需要 proxyConfig
        if ("web_search".equals(code)) {
            return new WebSearchTools(proxyConfig);
        }
        
        // BingSearchTools 需要 apiKey
        if ("bing_search".equals(code)) {
            return new BingSearchTools(bingSearchConfig.getApiKey());
        }
        
        throw new IllegalArgumentException("不支持的工具集: " + code);
    }

    @Override
    public boolean supports(String code) {
        return "web_search".equals(code) || "bing_search".equals(code);
    }
}
```

**使用场景**:
- `WebSearchTools` 需要 `HttpProxyConfig` 配置代理
- `BingSearchTools` 需要 API Key
- `NoteTools` 需要 workspace 路径参数

**注册方式**:
```java
// 在 AgentRegistry 中注册工厂
toolRegistry.registerFactory(builtinToolFactory);
```

#### 4. ToolController (工具管理控制器)

**位置**: [`ToolController.java`](file:///d:/claw-agent/backend/src/main/java/com/claw/agent/controller/ToolController.java)

**完整 API 端点列表**:
```java
// 查询类
@GetMapping("/list-with-details")           // 获取所有工具集及详情（推荐）
@GetMapping("/{code}/tools")                // 获取指定工具集的工具详情
@GetMapping("/list")                        // 获取所有工具集列表
@GetMapping("/enabled")                     // 获取已启用的工具集代码
@GetMapping("/category/{category}")         // 按分类获取工具集
@GetMapping("/{code}")                      // 获取单个工具集详情

// 管理类
@PostMapping("/{code}/enable")              // 启用工具集
@PostMapping("/{code}/disable")             // 禁用工具集
@PostMapping("/batch-enable")               // 批量启用工具集
@PostMapping("/batch-disable")              // 批量禁用工具集
```

---

## 💡 使用场景

### 1. 前端工具集管理页面

**展示效果**:
```
┌─────────────────────────────────────────────────────┐
│  平台工具集管理                                      │
├─────────────────────────────────────────────────────┤
│                                                     │
│  📁 实用工具 (utility) - 2个已启用                  │
│  ┌───────────────────────────────────────────────┐  │
│  │ ✓ system_tools - 系统工具 v1.0.0             │  │
│  │   提供时间查询、日期计算、UUID 生成...         │  │
│  │                                               │  │
│  │   包含工具 (3):                               │  │
│  │   • get_current_time - 获取当前时间           │  │
│  │   • calculate_date - 日期计算                 │  │
│  │   • generate_uuid - 生成 UUID                 │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │ ✓ math_tools - 数学工具 v1.0.0               │  │
│  │   提供数学计算、哈希函数、Base64 编解码...     │  │
│  │                                               │  │
│  │   包含工具 (5):                               │  │
│  │   • calculate - 执行数学表达式计算            │  │
│  │   • hash_md5 - MD5 哈希                       │  │
│  │   • base64_encode - Base64 编码               │  │
│  │   • base64_decode - Base64 解码               │  │
│  │   • generate_password - 生成随机密码          │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  🔍 search - 搜索工具 (1个)                         │
│  ┌───────────────────────────────────────────────┐  │
│  │ ✓ web_search - 联网搜索 v1.0.0               │  │
│  │   使用 DuckDuckGo 进行联网搜索...              │  │
│  │                                               │  │
│  │   包含工具 (1):                               │  │
│  │   • web_search - 联网搜索                     │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 2. 工具权限配置

管理员可以为不同角色配置可用的工具:
```json
{
  "role": "USER",
  "allowed_tools": [
    "system_tools.get_current_time",
    "math_tools.calculate"
  ],
  "denied_tools": [
    "system_tools.generate_uuid"
  ]
}
```

### 3. 工具使用统计

记录每个工具的调用次数、成功率等指标:
```sql
CREATE TABLE sys_tool_usage_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    tool_set_code VARCHAR(50) NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    call_time DATETIME NOT NULL,
    success BOOLEAN NOT NULL,
    duration_ms INT,
    INDEX idx_user_tool (user_id, tool_set_code, tool_name),
    INDEX idx_call_time (call_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具使用日志';
```

---

## 🚀 下一步优化建议

### 1. 前端工具详情页
- 点击工具集展开显示所有工具
- 显示工具的描述、参数、返回值类型
- 支持快速测试工具功能

### 2. 工具搜索功能
- 按工具名称搜索
- 按描述关键词搜索
- 按分类筛选

### 3. 工具版本历史
- 记录工具集的变更历史
- 支持回滚到旧版本
- 显示版本更新说明

### 4. 工具依赖图可视化
- 展示工具集之间的依赖关系
- 高亮显示循环依赖
- 自动生成依赖报告

---

## 📝 总结

通过新增 **10 个 API 端点**,平台现在可以完整感知和管理所有可用工具:

✅ **工具集级别**: 知道有哪些工具集(system_tools, math_tools, web_search)  
✅ **工具级别**: 知道每个工具集包含哪些具体工具(get_current_time, calculate 等)  
✅ **工具详情**: 知道每个工具的名称、描述、参数、返回值  
✅ **动态管理**: 支持运行时启用/禁用工具集,无需重启服务  
✅ **分类查询**: 按 utility/search/data/code/ai/system 分类筛选  
✅ **批量操作**: 支持批量启用/禁用多个工具集  
✅ **依赖检查**: 启用时自动验证依赖,禁用时检查反向依赖  
✅ **工厂模式**: 通过 ToolFactory 支持需要特殊参数的工具实例化  

这为后续的工具管理、权限控制、使用统计等功能奠定了坚实基础! 🎉
