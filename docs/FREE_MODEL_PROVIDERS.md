# 免费 AI 模型接入指南

## 📋 概述

本文档介绍如何为 claw-agent 平台接入**完全免费**或**有免费额度**的 AI 模型提供商,降低使用成本。

---

## 🆓 免费模型清单

### 1. Ollama (本地部署,完全免费) ⭐⭐⭐⭐⭐

**特点**:
- ✅ **零成本**: 无需 API Key,完全离线运行
- ✅ **隐私安全**: 数据在本地处理,不上传云端
- ✅ **模型丰富**: Llama 3.2、Qwen 2.5、DeepSeek-R1、Mistral 等
- ⚠️ **硬件要求**: 需要 8GB+ RAM,CPU/GPU 均可运行

**安装步骤**:
```bash
# 1. 下载并安装 Ollama
# Windows: https://ollama.ai/download/windows
# macOS: brew install ollama
# Linux: curl -fsSL https://ollama.ai/install.sh | sh

# 2. 拉取模型 (以 Llama 3.2 为例)
ollama pull llama3.2

# 3. 启动服务 (默认端口 11434)
ollama serve

# 4. 验证安装
curl http://localhost:11434/api/tags
```

**接入配置**:
```sql
-- V35 迁移脚本已配置,执行后在「系统管理」→「模型配置」中启用即可
UPDATE model_provider_config 
SET enabled = 1 
WHERE provider_name = 'ollama';
```

---

### 2. Groq (超快推理,免费额度充足) ⭐⭐⭐⭐⭐

**特点**:
- ✅ **极速推理**: 比 OpenAI GPT-4 快 10x
- ✅ **免费额度**: 每分钟 30 次请求 (个人开发足够)
- ✅ **模型优质**: Llama 3.3 70B、Mixtral 8x7B
- ⚠️ **需注册**: 需要 Groq 账号获取 API Key

**注册步骤**:
1. 访问 https://console.groq.com/
2. 使用 GitHub/Google 账号登录
3. 进入 **API Keys** 页面
4. 点击 **Create API Key**,复制生成的 `gsk_...` 密钥

**接入配置**:
```sql
-- 替换 YOUR_KEY 为真实 API Key
UPDATE model_provider_config 
SET api_key = 'gsk_YOUR_REAL_API_KEY',
    enabled = 1
WHERE provider_name = 'groq';
```

**免费额度说明**:
- 速率限制: 30 RPM (Requests Per Minute)
- 每日配额: 无明确上限,但超出合理范围会触发限流
- 适用场景: 日常聊天、代码生成、知识问答

---

### 3. Hugging Face Inference API (月度免费配额) ⭐⭐⭐⭐

**特点**:
- ✅ **模型海量**: 支持数千个开源模型
- ✅ **免费配额**: 每月 30k tokens (个人账号)
- ✅ **灵活切换**: 可随时更换模型
- ⚠️ **速率限制**: 免费层有并发限制

**注册步骤**:
1. 访问 https://huggingface.co/
2. 注册账号 (邮箱 + 密码)
3. 进入 **Settings** → **Access Tokens**
4. 点击 **New token**,选择 **Read** 权限,复制 `hf_...` 密钥

**接入配置**:
```sql
-- 替换 YOUR_TOKEN 为真实 Token
UPDATE model_provider_config 
SET api_key = 'hf_YOUR_REAL_TOKEN',
    enabled = 1
WHERE provider_name = 'huggingface';
```

**推荐模型**:
- `mistralai/Mistral-7B-Instruct-v0.3`: 通用对话
- `meta-llama/Llama-3.2-3B-Instruct`: 轻量级模型
- `codellama/CodeLlama-7b-Instruct`: 代码专用

---

### 4. 阿里云百炼 (新用户免费额度) ⭐⭐⭐⭐

**特点**:
- ✅ **中文强项**: Qwen 系列模型对中文理解优秀
- ✅ **新人福利**: 新用户赠送 100 万 token
- ✅ **国内加速**: 阿里云节点,访问速度快
- ⚠️ **需实名认证**: 注册后需完成实名认证

**注册步骤**:
1. 访问 https://dashscope.console.aliyun.com/
2. 使用阿里云账号登录 (需实名认证)
3. 进入 **API-KEY 管理**
4. 点击 **创建 API-KEY**,复制 `sk-...` 密钥

**接入配置**:
```sql
-- 替换 YOUR_KEY 为真实 API Key
UPDATE model_provider_config 
SET api_key = 'sk-YOUR_REAL_API_KEY',
    enabled = 1
WHERE provider_name = 'aliyun-bailian';
```

**推荐模型**:
- `qwen-plus`: 平衡性能与成本
- `qwen-max`: 最强推理能力
- `qwen-turbo`: 快速响应

---

### 5. DeepSeek (代码专用,每日免费) ⭐⭐⭐⭐

**特点**:
- ✅ **代码专家**: DeepSeek-Coder 在代码生成/审查方面表现优异
- ✅ **每日免费**: 每日赠送一定额度
- ✅ **中文友好**: 对中文注释/文档支持良好
- ⚠️ **需注册**: 需要 DeepSeek 账号

**注册步骤**:
1. 访问 https://platform.deepseek.com/
2. 使用手机号/邮箱注册
3. 进入 **API Keys** 页面
4. 点击 **创建新密钥**,复制 `sk-...` 密钥

**接入配置**:
```sql
-- 替换 YOUR_KEY 为真实 API Key
UPDATE model_provider_config 
SET api_key = 'sk-YOUR_REAL_API_KEY',
    enabled = 1
WHERE provider_name = 'deepseek';
```

**推荐模型**:
- `deepseek-chat`: 通用对话
- `deepseek-coder`: 代码生成/补全/调试

---

## 🎯 推荐组合方案

| 使用场景 | 首选模型 | 备选模型 | 理由 |
|---------|---------|---------|------|
| **日常聊天/知识问答** | Ollama (Llama 3.2) | Groq (Llama 3.3 70B) | 完全免费 vs 速度更快 |
| **代码生成/审查** | Groq (Mixtral 8x7B) | DeepSeek-Coder | 推理速度快 vs 代码专业性强 |
| **中文内容创作** | 阿里云百炼 (Qwen-Plus) | DeepSeek-Chat | 中文理解强 vs 每日免费 |
| **数据分析/实验** | Hugging Face (Mistral) | Ollama (Qwen 2.5) | 模型多样 vs 离线可用 |
| **隐私敏感任务** | Ollama (本地) | - | 数据不出本地 |

---

## 📝 快速开始

### Step 1: 执行数据库迁移

```bash
cd d:\claw-agent\backend
mvn flyway:migrate
```

这将执行 `V35__add_free_model_providers.sql`,在数据库中插入 5 个模型提供商配置。

### Step 2: 配置 API Key (如需)

对于需要 API Key 的模型 (Groq/HuggingFace/阿里云/DeepSeek):

1. 登录对应平台获取 API Key
2. 在 claw-agent 前端进入 **系统管理** → **模型配置**
3. 找到对应提供商,点击 **编辑**
4. 填入 API Key,保存
5. 将 **启用状态** 切换为 **开启**

**Ollama 无需配置 API Key**,只需确保本地服务正在运行即可。

### Step 3: 测试连接

在 **模型配置** 页面,点击每个提供商的 **测试连接** 按钮,验证配置是否正确。

### Step 4: 设置默认模型

在 **系统配置** → **Agent 配置** 中,选择您偏好的模型作为默认推理引擎。

---

## 🔧 故障排查

### 问题 1: Ollama 连接失败

**现象**: `Connection refused: localhost:11434`

**解决**:
```bash
# 检查 Ollama 是否运行
ollama list

# 如果没有输出,启动服务
ollama serve

# 验证端口监听
netstat -ano | findstr ":11434"
```

### 问题 2: Groq/HuggingFace 返回 401 Unauthorized

**现象**: API 调用返回认证错误

**解决**:
1. 检查 API Key 是否正确复制 (注意前后空格)
2. 确认 Key 未过期 (部分平台有有效期限制)
3. 重新生成 API Key 并更新配置

### 问题 3: 阿里云百炼返回 403 Forbidden

**现象**: 认证通过但请求被拒绝

**解决**:
1. 确认已完成**实名认证**
2. 检查账户余额 (免费额度是否用尽)
3. 查看阿里云控制台是否有欠费记录

### 问题 4: 模型响应速度慢

**现象**: Ollama 推理耗时过长 (>30s)

**解决**:
1. **降低模型参数量**: 从 70B 切换到 7B/8B 版本
2. **启用 GPU 加速**: 
   ```bash
   # NVIDIA GPU
   ollama run llama3.2 --gpu
   
   # Apple Silicon (M1/M2/M3)
   ollama run llama3.2 --metal
   ```
3. **增加内存分配**: 确保系统有足够空闲 RAM

---

## 💰 成本控制建议

### 1. 优先使用 Ollama (完全免费)
- 适合: 日常聊天、知识问答、简单代码生成
- 优势: 零成本、隐私安全、无速率限制

### 2. 合理使用 Groq (高速免费)
- 适合: 需要快速响应的场景 (实时对话、代码补全)
- 注意: 避免高频批量请求 (可能触发限流)

### 3. 新用户充分利用阿里云/DeepSeek 福利
- 阿里云: 100 万 token 免费额度 (~相当于 500 次长对话)
- DeepSeek: 每日免费额度 (~相当于 50-100 次对话)
- 建议: 用于重要任务 (复杂代码审查、长篇内容创作)

### 4. 监控 Token 消耗
- 定期查看 **Token 统计** 页面 (`/token-usage`)
- 分析各模型的用量分布
- 调整默认模型优先级

---

## 📊 性能对比

| 模型提供商 | 推理速度 | 中文质量 | 代码能力 | 免费额度 | 稳定性 |
|-----------|---------|---------|---------|---------|--------|
| Ollama (Llama 3.2) | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ♾️ 无限 | ⭐⭐⭐⭐⭐ |
| Groq (Llama 3.3 70B) | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| Hugging Face (Mistral) | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |
| 阿里云百炼 (Qwen) | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| DeepSeek (Coder) | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |

---

## 🔄 后续扩展

### 已接入的模型提供商
- ✅ **Anthropic Claude**: claude-sonnet-4-20250514，Claude 3.5/3.7/4 系列
- ✅ **Google Gemini**: gemini-2.0-flash，Google Gemini 2.0/2.5
- ✅ **火山方舟/豆包**: doubao-seed-2-1-pro，火山方舟 OpenAI 兼容协议

### 计划添加的免费模型
- **Cohere**: 每月 100 次免费调用 (文本嵌入/分类)
- **Azure OpenAI**: 新用户 $200 赠金 (首月)

### 自定义模型接入
如需接入其他 OpenAI 兼容的模型提供商,可在 **系统管理** → **模型配置** 中手动添加:

```sql
INSERT INTO model_provider_config (
    provider_name, provider_type, api_key, base_url, 
    default_model, models, enabled, scope, remark
) VALUES (
    'your-provider',
    'OPENAI_COMPATIBLE',
    'YOUR_API_KEY',
    'https://api.your-provider.com/v1',
    'default-model-name',
    '["model-1", "model-2"]',
    1,
    'PLATFORM',
    '自定义模型提供商描述'
);
```

---

## 📚 参考资料

- [Ollama 官方文档](https://ollama.ai/docs)
- [Groq API 文档](https://console.groq.com/docs)
- [Hugging Face Inference API](https://huggingface.co/docs/api-inference)
- [阿里云百炼文档](https://help.aliyun.com/product/334390.htm)
- [DeepSeek API 文档](https://platform.deepseek.com/api-docs)

---

**最后更新**: 2026-08-27  
**维护者**: claw-agent 团队
