# HTTP 代理配置指南

## 概述

Claw Agent 的联网搜索功能（`search` 工具，由 `multi_search` 工具集提供）支持多引擎多级降级（Tavily → Brave → Bing → SearXNG → DuckDuckGo）。其中 DuckDuckGo 和 SearXNG 在中国大陆网络环境下需要配置 HTTP 代理才能正常访问。

## 配置方式

### 方式 1：环境变量（推荐用于生产环境）

在启动应用前设置以下环境变量：

```bash
# Linux/Mac
export HTTP_PROXY_HOST=127.0.0.1
export HTTP_PROXY_PORT=7890

# Windows PowerShell
$env:HTTP_PROXY_HOST="127.0.0.1"
$env:HTTP_PROXY_PORT="7890"

# Windows CMD
set HTTP_PROXY_HOST=127.0.0.1
set HTTP_PROXY_PORT=7890
```

然后启动应用：

```bash
cd backend
java -jar target/backend-1.0.0-SNAPSHOT.jar
```

### 方式 2：application.yml 配置

编辑 `backend/src/main/resources/application.yml`，添加代理配置：

```yaml
claw:
  proxy:
    http:
      host: 127.0.0.1    # 代理主机地址
      port: 7890         # 代理端口
```

### 方式 3：IDEA 运行配置

如果使用 IntelliJ IDEA 开发：

1. 打开 Run/Debug Configurations
2. 找到 `ClawAgentApplication` 配置
3. 在 "Environment variables" 中添加：
   ```
   HTTP_PROXY_HOST=127.0.0.1;HTTP_PROXY_PORT=7890
   ```

## 常见代理软件配置

### Clash

Clash 默认监听端口为 `7890`：

```yaml
claw:
  proxy:
    http:
      host: 127.0.0.1
      port: 7890
```

### V2Ray / Xray

V2Ray 默认监听端口为 `10809`（HTTP 代理）：

```yaml
claw:
  proxy:
    http:
      host: 127.0.0.1
      port: 10809
```

### Shadowsocks + Privoxy

Shadowsocks 本身不提供 HTTP 代理，需要配合 Privoxy：

1. 安装 Privoxy
2. 配置 Privoxy 转发到 Shadowsocks SOCKS5 代理
3. Privoxy 默认监听 `8118` 端口

```yaml
claw:
  proxy:
    http:
      host: 127.0.0.1
      port: 8118
```

## 验证配置

启动应用后，查看日志确认代理是否生效：

```
INFO WebSearchTools: 已启用代理 127.0.0.1:7890
```

如果看到以下日志，说明未配置代理或配置无效：

```
DEBUG WebSearchTools: 未配置代理，使用直连模式
```

## 测试联网搜索

1. 登录 Claw Agent 平台
2. 发送消息："请搜索一下今天的新闻"
3. 观察是否能正常返回搜索结果

如果仍然失败，检查：
- 代理软件是否正常运行
- 代理端口是否正确
- 防火墙是否阻止了连接

## 注意事项

1. **仅影响联网搜索**：代理配置只影响 `multi_search` 工具集中的搜索引擎，不影响其他功能
2. **多引擎降级**：配置了 Tavily/Brave/Bing API Key 时优先使用，无需代理；未配置时降级到 SearXNG/DuckDuckGo，需要代理
3. **动态生效**：修改代理配置后需要重启应用才能生效
4. **安全性**：生产环境建议使用可信的代理服务，避免敏感信息泄露
5. **超时时间**：各引擎独立超时控制，总超时 25 秒

## 故障排查

### 问题 1：配置了代理但仍然无法搜索

**可能原因**：
- 代理软件未启动
- 代理端口错误
- 代理服务器本身无法访问 DuckDuckGo

**解决方法**：
1. 检查代理软件状态
2. 使用浏览器测试代理是否正常
3. 查看应用日志中的代理配置信息

### 问题 2：搜索很慢或超时

**可能原因**：
- 代理服务器延迟高
- 网络不稳定

**解决方法**：
1. 更换更快的代理服务器
2. 检查网络连接
3. 考虑增加超时时间（修改 `WebSearchTools.java` 中的 `Duration.ofSeconds(15)`）

### 问题 3：不想使用代理

**解决方法**：
1. 删除环境变量或 application.yml 中的代理配置
2. 重启应用
3. 应用将使用直连模式（在中国大陆可能无法访问 DuckDuckGo）

或者考虑使用其他搜索引擎 API（如 Bing、Google Custom Search 等），这些可能需要申请 API Key。
