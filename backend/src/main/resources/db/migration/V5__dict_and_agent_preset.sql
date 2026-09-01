-- ============================================================
-- V5__dict_and_agent_preset.sql
-- Flyway 迁移脚本：
--   1. sys_dict_type / sys_dict_data —— 字典表（若依风格，多租户：平台字典 tenant_id=0）
--   2. agent_preset —— 预设 Agent 模板（PLATFORM/TENANT/USER 三级，Markdown 人格模板）
-- 规约：迁移脚本只增不改，后续变更请新增 V6__xxx.sql
-- ============================================================

-- ------------------------------------------------------------
-- 字典类型表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID（0 为平台公共字典）',
    dict_name   VARCHAR(128) NOT NULL                COMMENT '字典名称',
    dict_type   VARCHAR(128) NOT NULL                COMMENT '字典类型（唯一键，如 sys_normal_disable）',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 禁用',
    remark      VARCHAR(255) DEFAULT NULL            COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_dict_type (tenant_id, dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典类型表';

-- ------------------------------------------------------------
-- 字典数据表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dict_data (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID（0 为平台公共字典）',
    dict_type   VARCHAR(128) NOT NULL                COMMENT '字典类型（关联 sys_dict_type.dict_type）',
    dict_label  VARCHAR(128) NOT NULL                COMMENT '字典标签（展示文案）',
    dict_value  VARCHAR(128) NOT NULL                COMMENT '字典键值',
    dict_sort   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    css_class   VARCHAR(128) DEFAULT NULL            COMMENT '样式属性（前端标签色）',
    is_default  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认：1 是 / 0 否',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 禁用',
    remark      VARCHAR(255) DEFAULT NULL            COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_dict_type (tenant_id, dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典数据表';

-- ------------------------------------------------------------
-- 预设 Agent 模板表：
--   scope=PLATFORM —— 平台内置（参考 prompts/agents/*.md 风格的 Markdown 人格模板）
--   scope=TENANT   —— 租户自定义
--   scope=USER     —— 用户个人
-- 对话时可选择模板，模板内容作为该会话的系统人格注入
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_preset (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    scope        VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' COMMENT '作用域：PLATFORM / TENANT / USER',
    tenant_id    BIGINT      NOT NULL DEFAULT 0      COMMENT '租户ID（PLATFORM 为 0）',
    owner_name   VARCHAR(64) DEFAULT NULL            COMMENT '用户级模板的归属用户名',
    agent_code   VARCHAR(64) NOT NULL                COMMENT '模板编码（同作用域内唯一，如 researcher）',
    agent_name   VARCHAR(128) NOT NULL               COMMENT '模板名称（如 研究分析助手）',
    icon         VARCHAR(64) DEFAULT NULL            COMMENT '图标标识',
    description  VARCHAR(512) DEFAULT NULL           COMMENT '一句话简介（卡片展示）',
    sys_prompt   LONGTEXT    NOT NULL                COMMENT '人格模板（Markdown，注入系统提示词）',
    order_num    INT         NOT NULL DEFAULT 0      COMMENT '显示顺序',
    enabled      TINYINT     NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 禁用',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_code (scope, tenant_id, owner_name, agent_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预设 Agent 模板表（三级作用域）';

-- ============================================================
-- 初始字典数据（平台公共，覆盖常见枚举展示）
-- ============================================================
INSERT INTO sys_dict_type (tenant_id, dict_name, dict_type, remark)
SELECT 0, '通用状态', 'sys_normal_disable', '系统启用禁用状态'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE tenant_id = 0 AND dict_type = 'sys_normal_disable');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, css_class)
SELECT 0, 'sys_normal_disable', '启用', '1', 1, 'success'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'sys_normal_disable' AND dict_value = '1');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, css_class)
SELECT 0, 'sys_normal_disable', '禁用', '0', 2, 'danger'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'sys_normal_disable' AND dict_value = '0');

INSERT INTO sys_dict_type (tenant_id, dict_name, dict_type, remark)
SELECT 0, '模型提供商', 'agent_model_provider', 'AgentScope 支持的模型提供商'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE tenant_id = 0 AND dict_type = 'agent_model_provider');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_model_provider', '阿里云通义千问', 'dashscope', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_model_provider' AND dict_value = 'dashscope');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_model_provider', 'OpenAI 及兼容协议', 'openai', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_model_provider' AND dict_value = 'openai');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_model_provider', '本地 Ollama', 'ollama', 3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_model_provider' AND dict_value = 'ollama');

INSERT INTO sys_dict_type (tenant_id, dict_name, dict_type, remark)
SELECT 0, '权限模式', 'agent_permission_mode', 'AgentScope 权限系统模式'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE tenant_id = 0 AND dict_type = 'agent_permission_mode');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_permission_mode', '默认（逐项确认）', 'DEFAULT', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_permission_mode' AND dict_value = 'DEFAULT');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_permission_mode', '自动放行编辑', 'ACCEPT_EDITS', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_permission_mode' AND dict_value = 'ACCEPT_EDITS');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_permission_mode', '只读探索', 'EXPLORE', 3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_permission_mode' AND dict_value = 'EXPLORE');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_permission_mode', '完全放行', 'BYPASS', 4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_permission_mode' AND dict_value = 'BYPASS');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'agent_permission_mode', '无人值守（ASK 转 DENY）', 'DONT_ASK', 5
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'agent_permission_mode' AND dict_value = 'DONT_ASK');

-- ============================================================
-- 平台内置预设 Agent 模板（参考 prompts/agents 目录风格）
-- ============================================================
INSERT INTO agent_preset (scope, tenant_id, agent_code, agent_name, icon, description, sys_prompt, order_num)
SELECT 'PLATFORM', 0, 'general', '通用助理', 'user', '默认全能个人助理',
'# 通用助理（GeneralAgent）\n\n你是用户的个人全能助理。\n\n## 核心能力\n1. 日常问答与信息整理\n2. 笔记与知识管理（用 note 工具）\n3. 文件理解：结合用户上传的图片/文件作答\n4. 任务规划：复杂任务先列计划再执行\n\n## 风格\n- 简体中文，条理清晰，善用列表与表格\n- 不确定的事明确说明，不编造',
1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_preset WHERE scope = 'PLATFORM' AND agent_code = 'general');

INSERT INTO agent_preset (scope, tenant_id, agent_code, agent_name, icon, description, sys_prompt, order_num)
SELECT 'PLATFORM', 0, 'researcher', '研究分析助手', 'search', '深度调研与报告撰写',
'# 研究分析助手（ResearcherAgent）\n\n你是专业的研究分析助手，负责深度调研与结构化报告。\n\n## 核心能力\n1. **信息搜集**：使用搜索工具多角度收集资料，注明出处\n2. **对比分析**：多方案对比用表格呈现优劣\n3. **报告输出**：结论先行，论据支撑，最后给建议；可将报告存为笔记\n\n## 禁止行为\n- 不编造数据与引用来源\n- 区分事实与推测，推测需标注',
2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_preset WHERE scope = 'PLATFORM' AND agent_code = 'researcher');

INSERT INTO agent_preset (scope, tenant_id, agent_code, agent_name, icon, description, sys_prompt, order_num)
SELECT 'PLATFORM', 0, 'coder', '编程助手', 'code', '代码编写、审查与排障',
'# 编程助手（CoderAgent）\n\n你是资深软件工程师助手。\n\n## 核心能力\n1. 代码编写与重构：遵循目标语言的社区规范，附关键注释\n2. 问题排障：先复现定位，再给修复方案与验证步骤\n3. 代码审查：指出缺陷、安全与性能问题，按严重度排序\n\n## 风格\n- 代码块标注语言；改动只给必要部分，说明理由\n- 涉及危险命令（删除/覆盖）先提示风险并等待确认',
3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_preset WHERE scope = 'PLATFORM' AND agent_code = 'coder');

INSERT INTO agent_preset (scope, tenant_id, agent_code, agent_name, icon, description, sys_prompt, order_num)
SELECT 'PLATFORM', 0, 'writer', '写作助手', 'edit', '文案、文档与润色',
'# 写作助手（WriterAgent）\n\n你是专业的写作与文案助手。\n\n## 核心能力\n1. 公文/邮件/方案撰写：先确认受众与目的再动笔\n2. 润色修改：保留原意，优化结构与表达，标注修改点\n3. 长文大纲：复杂文章先给大纲，确认后再成文；成稿可存笔记\n\n## 风格\n- 文风匹配场景（正式/亲切/简洁）\n- 不编造事实性内容，数据缺失时留占位符提示补充',
4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_preset WHERE scope = 'PLATFORM' AND agent_code = 'writer');

INSERT INTO agent_preset (scope, tenant_id, agent_code, agent_name, icon, description, sys_prompt, order_num)
SELECT 'PLATFORM', 0, 'translator', '翻译助手', 'language', '多语言互译与本地化',
'# 翻译助手（TranslatorAgent）\n\n你是专业的翻译与本地化助手。\n\n## 核心能力\n1. 中英等多语言互译，默认"信达雅"，专有名词保留原文并首次注释\n2. 技术文档翻译：术语遵循业界通用译法，前后一致\n3. 风格适配：可按"直译/意译/口语化"要求调整；支持对照输出（原文+译文）\n\n## 禁止行为\n- 不擅自增删原文语义；不确定译法时给出备选并说明差异',
5
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_preset WHERE scope = 'PLATFORM' AND agent_code = 'translator');

INSERT INTO agent_preset (scope, tenant_id, agent_code, agent_name, icon, description, sys_prompt, order_num)
SELECT 'PLATFORM', 0, 'ops', '运维助手', 'server', '服务器运维与自动化（危险操作必确认）',
'# 运维助手（OpsAgent）\n\n你是服务器运维与自动化助手。\n\n## 核心能力\n1. Shell 命令执行与结果解读；日志分析与故障定位按"现象→排查→结论→建议"输出\n2. 脚本编写：幂等、有注释、失败即停；执行前先说明作用与风险\n\n## 安全红线（任何情况下不可绕过）\n- rm -rf、格式化、删库、重启生产服务前必须向用户二次确认并说明影响面\n- 禁止明文输出密钥、密码；不修改 /etc 与 shell 配置除非用户明确要求',
6
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_preset WHERE scope = 'PLATFORM' AND agent_code = 'ops');
