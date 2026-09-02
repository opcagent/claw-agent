# Claw Agent 工具集清单

本文档列出 Claw Agent 当前支持的所有工具及其使用方法。

## 工具分类

### 1. 笔记与知识管理工具 📝

#### `list_notes`
- **描述**:列出知识库中的所有笔记文件名
- **参数**:无
- **示例**:
  ```
  请列出我所有的笔记
  ```

#### `read_note`
- **描述**:读取指定笔记的完整内容
- **参数**:
  - `name`:笔记文件名(如:todo.md)
- **示例**:
  ```
  请读取笔记 todo.md 的内容
  ```

#### `save_note`
- **描述**:保存(创建或覆盖)一条笔记,内容为 Markdown 格式
- **参数**:
  - `name`:笔记文件名(必须以 .md 结尾)
  - `content`:笔记内容(Markdown)
- **示例**:
  ```
  请创建一条笔记,文件名为 meeting-notes.md,内容为今天的会议纪要
  ```

#### `append_note`
- **描述**:在指定笔记末尾追加内容;笔记不存在时自动创建
- **参数**:
  - `name`:笔记文件名
  - `content`:要追加的内容(Markdown)
- **示例**:
  ```
  请在笔记 todo.md 中追加一项新任务
  ```

---

### 2. 联网搜索工具 🔍

#### `search`
- **描述**:联网搜索网页信息，支持多引擎多级降级（Tavily → Brave → Bing → SearXNG → DuckDuckGo）
- **参数**:
  - `query`:搜索关键词
  - `num_results`:返回结果数量（默认5，最大8）
- **示例**:
  ```
  请搜索 A股今天开盘情况
  请搜索最新的 AI 技术发展趋势
  ```

**注意**:
- 不配 API Key 时自动跳过对应引擎，最终兑底 DuckDuckGo
- DuckDuckGo 需要配置 HTTP 代理（中国大陆网络环境）
- 详见 [PROXY_CONFIG.md](./PROXY_CONFIG.md) 和 [SEARCH_OPTIONS_COMPARISON.md](./SEARCH_OPTIONS_COMPARISON.md)

---

### 3. 浏览器自动化工具 🌐

#### `browse_url`
- **描述**:访问指定 URL，获取页面主要内容（纯文本）
- **参数**:
  - `url`:要访问的网页地址（仅支持 HTTP/HTTPS）
- **示例**:
  ```
  请浏览 https://example.com 并总结内容
  ```

#### `get_page_title`
- **描述**:获取指定 URL 的网页标题
- **参数**:
  - `url`:要获取标题的网页地址
- **示例**:
  ```
  这个网页的标题是什么？https://example.com
  ```

#### `extract_links`
- **描述**:提取页面中的所有超链接，返回链接文本和 URL
- **参数**:
  - `url`:要提取链接的网页地址
- **示例**:
  ```
  请提取这个页面的所有链接
  ```

**安全限制**:
- 仅支持 HTTP/HTTPS 协议
- 请求超时 30 秒
- 内容上限 2MB
- 内容截取前 5000 字符

---

### 4. 系统与时间工具 ⏰

#### `get_current_time`
- **描述**:获取当前系统时间(包含时区信息)
- **参数**:无
- **示例**:
  ```
  现在几点了?
  ```

#### `get_time_by_timezone`
- **描述**:获取指定时区的当前时间
- **参数**:
  - `timezone`:时区名称(如:Asia/Shanghai, America/New_York)
- **示例**:
  ```
  纽约现在几点?
  伦敦的时间是多少?
  ```

#### `days_between_dates`
- **描述**:计算两个日期之间的天数差
- **参数**:
  - `date1`:第一个日期(格式:yyyy-MM-dd)
  - `date2`:第二个日期(格式:yyyy-MM-dd)
- **示例**:
  ```
  2024-01-01 到 2024-12-31 有多少天?
  ```

#### `add_days_to_date`
- **描述**:在指定日期上增加或减少天数
- **参数**:
  - `date`:基准日期(格式:yyyy-MM-dd)
  - `days`:要增加或减少的天数(负数表示减少)
- **示例**:
  ```
  从今天起30天后是哪天?
  2024-01-01 往前推90天是哪天?
  ```

#### `generate_uuid`
- **描述**:生成一个唯一的 UUID v4 标识符
- **参数**:无
- **示例**:
  ```
  生成一个唯一ID
  ```

#### `get_system_info`
- **描述**:获取当前系统的 Java 运行时环境信息
- **参数**:无
- **示例**:
  ```
  查看系统信息
  ```

#### `timestamp_to_datetime`
- **描述**:将 Unix 时间戳转换为可读的日期时间
- **参数**:
  - `timestamp`:Unix 时间戳(秒)
- **示例**:
  ```
  时间戳 1700000000 对应什么时间?
  ```

#### `get_day_of_week`
- **描述**:获取指定日期是星期几
- **参数**:
  - `date`:日期(格式:yyyy-MM-dd)
- **示例**:
  ```
  2024-01-01 是星期几?
  ```

---

### 4. 数学计算与编码工具 🔢

#### `calculate`
- **描述**:执行数学计算,支持加减乘除和括号
- **参数**:
  - `expression`:数学表达式
- **示例**:
  ```
  计算 (10 + 5) * 2 / 3
  ```

#### `sqrt`
- **描述**:计算一个数的平方根
- **参数**:
  - `number`:要计算平方根的数字
- **示例**:
  ```
  16 的平方根是多少?
  ```

#### `power`
- **描述**:计算幂次方
- **参数**:
  - `base`:底数
  - `exponent`:指数
- **示例**:
  ```
  2 的 10 次方是多少?
  ```

#### `trigonometric`
- **描述**:计算三角函数(sin, cos, tan),输入为角度(度)
- **参数**:
  - `function`:函数名称(sin/cos/tan)
  - `angle`:角度(度)
- **示例**:
  ```
  sin(30度) 的值是多少?
  ```

#### `logarithm`
- **描述**:计算对数,支持自然对数和指定底数的对数
- **参数**:
  - `number`:真数
  - `base`:底数(可选,默认为自然对数 e)
- **示例**:
  ```
  log_2(1024) 等于多少?
  ln(e) 等于多少?
  ```

#### `random_int`
- **描述**:生成指定范围内的随机整数
- **参数**:
  - `min`:最小值(包含)
  - `max`:最大值(包含)
- **示例**:
  ```
  生成一个 1 到 100 的随机数
  ```

#### `generate_password`
- **描述**:生成随机密码,包含大小写字母、数字和特殊字符
- **参数**:
  - `length`:密码长度(默认16,范围8-128)
- **示例**:
  ```
  生成一个20位的强密码
  ```

#### `md5_hash`
- **描述**:计算字符串的 MD5 哈希值
- **参数**:
  - `input`:要计算哈希的字符串
- **示例**:
  ```
  计算 "hello world" 的 MD5 值
  ```

#### `sha256_hash`
- **描述**:计算字符串的 SHA-256 哈希值
- **参数**:
  - `input`:要计算哈希的字符串
- **示例**:
  ```
  计算密码的 SHA-256 哈希值
  ```

#### `base64_encode`
- **描述**:将字符串进行 Base64 编码
- **参数**:
  - `input`:要编码的字符串
- **示例**:
  ```
  将 "Hello World" 进行 Base64 编码
  ```

#### `base64_decode`
- **描述**:将 Base64 编码的字符串解码
- **参数**:
  - `input`:Base64 编码的字符串
- **示例**:
  ```
  解码 SGVsbG8gV29ybGQ=
  ```

#### `unit_convert`
- **描述**:单位换算,支持长度、重量、温度
- **参数**:
  - `value`:数值
  - `fromUnit`:源单位
  - `toUnit`:目标单位
- **支持的单位**:
  - **长度**:m(米), km(千米), cm(厘米), mm(毫米), in(英寸), ft(英尺)
  - **重量**:kg(千克), g(克), lb(磅), oz(盎司)
  - **温度**:C(摄氏度), F(华氏度), K(开尔文)
- **示例**:
  ```
  100 公里等于多少英里?
  72 华氏度等于多少摄氏度?
  1 公斤等于多少磅?
  ```

---

## 工具使用最佳实践

### 1. 组合使用工具

Agent 可以组合多个工具来完成复杂任务:

```
用户: 帮我规划一下从北京到上海的行程

Agent 可能使用的工具:
1. search: 搜索高铁时刻表
2. browse_url: 查看官网详情
3. get_current_time: 获取当前时间
4. add_days_to_date: 计算出发日期
5. save_note: 保存行程计划到笔记
```

### 2. 明确参数格式

- 日期格式统一使用 `yyyy-MM-dd`
- 时区使用 IANA 时区名称(如 `Asia/Shanghai`)
- 数学表达式只允许数字和运算符

### 3. 错误处理

如果工具调用失败,Agent 会收到错误提示并尝试其他方式。例如:

```
用户: 计算 sqrt(-4)
Agent: 无法对负数求平方根,是否需要复数计算功能?
```

### 4. 安全限制

- `calculate` 工具只允许基本数学运算,防止代码注入
- 密码生成器限制最大长度为128位,防止资源滥用
- 所有工具都有超时保护

---

## 添加工具的开发指南

如果你想添加自定义工具,请参考以下步骤:

### 步骤 1: 创建工具类

```java
package com.claw.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyCustomTools {

    @Tool(name = "my_tool", description = "工具描述")
    public String myMethod(
            @ToolParam(name = "param1", description = "参数1说明") String param1,
            @ToolParam(name = "param2", description = "参数2说明", required = false) Integer param2) {
        try {
            // 业务逻辑
            return "结果";
        } catch (Exception e) {
            log.error("Tool execution error", e);
            return "错误: " + e.getMessage();
        }
    }
}
```

### 步骤 2: 在 AgentRegistry 中注册

编辑 `AgentRegistry.java`,在 `buildHarnessAgent` 方法中添加:

```java
toolkit.registerTool(new MyCustomTools());
```

### 步骤 3: (可选) 添加工具开关

如果需要控制工具的启用/禁用:

1. 在 `CapabilityService` 中添加常量
2. 在数据库 `sys_capability` 表中添加记录
3. 在 `AgentRegistry` 中使用条件判断

```java
if (capabilityService.isToolEnabled(CapabilityService.TOOL_MY_CUSTOM, tenantId, username)) {
    toolkit.registerTool(new MyCustomTools());
}
```

### 步骤 4: 编写测试

为工具编写单元测试,确保边界情况正确处理。

---

## 参考的开源项目

本工具集设计参考了以下成熟开源项目:

1. **LangChain Tools** - https://python.langchain.com/docs/integrations/tools/
   - 提供了丰富的工具实现思路

2. **LlamaIndex Tools** - https://llamahub.ai/?tab=tools
   - 数据查询和处理工具的优秀实践

3. **AgentScope Tools** - https://github.com/modelscope/agentscope
   - 官方工具集的架构设计

4. **Microsoft Semantic Kernel** - https://github.com/microsoft/semantic-kernel
   - 插件化设计的参考

5. **AutoGen** - https://github.com/microsoft/autogen
   - 多 Agent 协作中的工具共享机制

---

## 更新日志

### 2026-09-02
- ✅ 新增 BrowserTools: 浏览器自动化（browse_url / get_page_title / extract_links）
- ✅ 搜索工具统一为 `search`，支持 Tavily/Brave/Bing/SearXNG/DuckDuckGo 多级降级

### 2026-08-26
- ✅ 新增 SystemTools: 时间查询、日期计算、UUID 生成、系统信息
- ✅ 新增 MathTools: 数学计算、哈希函数、Base64 编解码、单位换算、密码生成

### 未来计划
- [ ] 文本处理工具（翻译、摘要、JSON/XML 格式化）
- [ ] 代码相关工具（正则测试、语法检查）
- [ ] 网络工具（IP 查询、URL 解析）
- [ ] 实用小工具（QR Code 生成、颜色转换）

---

## 问题反馈

如果你发现工具有 bug 或有新的工具需求,请提交 Issue 或 PR。
