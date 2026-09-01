-- ============================================================
-- V6__pipeline_and_template.sql
-- Flyway 迁移脚本（参考 prompts/ 资产目录设计）：
--   1. agent_pipeline   —— 编排流水线模板（多 Agent 顺序/协作步骤，三级作用域）
--   2. prompt_template  —— 提示词模板（{{变量}} 占位符，三级作用域）
-- 规约：迁移脚本只增不改，后续变更请新增 V7__xxx.sql
-- ============================================================

-- ------------------------------------------------------------
-- 编排流水线模板表：把多步骤任务固化为可复用的执行剧本
--   运行时由主 Agent 按 steps 依次执行（或委派子 Agent），
--   exception_handling 定义异常分支策略
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_pipeline (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    scope              VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM' COMMENT '作用域：PLATFORM / TENANT / USER',
    tenant_id          BIGINT       NOT NULL DEFAULT 0      COMMENT '租户ID（PLATFORM 为 0）',
    owner_name         VARCHAR(64)  DEFAULT NULL            COMMENT '用户级流水线的归属用户名',
    pipeline_code      VARCHAR(64)  NOT NULL                COMMENT '流水线编码（同作用域内唯一）',
    pipeline_name      VARCHAR(128) NOT NULL                COMMENT '流水线名称',
    description        VARCHAR(512) DEFAULT NULL            COMMENT '流程描述',
    steps              LONGTEXT     NOT NULL                COMMENT '执行步骤（Markdown：Step N + 动作 + 输出）',
    exception_handling TEXT         DEFAULT NULL            COMMENT '异常处理策略（Markdown）',
    order_num          INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    enabled            TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 禁用',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_code (scope, tenant_id, owner_name, pipeline_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '编排流水线模板表（三级作用域）';

-- ------------------------------------------------------------
-- 提示词模板表：含 {{变量}} 占位符，前端填参后发送给 Agent
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prompt_template (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    scope         VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM' COMMENT '作用域：PLATFORM / TENANT / USER',
    tenant_id     BIGINT       NOT NULL DEFAULT 0      COMMENT '租户ID（PLATFORM 为 0）',
    owner_name    VARCHAR(64)  DEFAULT NULL            COMMENT '用户级模板的归属用户名',
    template_code VARCHAR(64)  NOT NULL                COMMENT '模板编码（同作用域内唯一）',
    template_name VARCHAR(128) NOT NULL                COMMENT '模板名称',
    category      VARCHAR(64)  DEFAULT NULL            COMMENT '分类（如 写作 / 汇报 / 分析）',
    content       LONGTEXT     NOT NULL                COMMENT '模板内容（含 {{变量}} 占位符）',
    order_num     INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    enabled       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 禁用',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_code (scope, tenant_id, owner_name, template_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '提示词模板表（三级作用域）';

-- ============================================================
-- 内置流水线种子数据
-- ============================================================
INSERT INTO agent_pipeline (scope, tenant_id, pipeline_code, pipeline_name, description, steps, exception_handling, order_num)
SELECT 'PLATFORM', 0, 'research-report', '调研报告流水线',
'从选题到成稿的三步调研流程：搜集 → 分析 → 成稿入库',
'## 执行步骤（顺序执行）\n### Step 1: 信息搜集（researcher）\n- 围绕主题多轮搜索，收集不少于 5 个来源，记录出处链接；输出：要点清单 + 来源列表；保存笔记 research-{主题}-raw.md\n### Step 2: 对比分析（researcher）\n- 交叉验证要点清单，剔除孤证与冲突信息；输出：结构化分析结论（含表格）；保存笔记 research-{主题}-analysis.md\n### Step 3: 成稿入库（writer）\n- 按"结论先行 → 论据 → 建议"成稿，引用 Step1 来源；输出：最终报告笔记 research-{主题}-final.md',
'## 异常处理\n- 来源不足 3 个 → 扩大关键词再搜一轮，仍不足则在报告中标注"资料有限"\n- 信息互相矛盾 → 保留双方观点并标注分歧，不擅自裁决',
1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_pipeline WHERE scope = 'PLATFORM' AND pipeline_code = 'research-report');

INSERT INTO agent_pipeline (scope, tenant_id, pipeline_code, pipeline_name, description, steps, exception_handling, order_num)
SELECT 'PLATFORM', 0, 'doc-writing', '长文写作流水线',
'大纲 → 初稿 → 润色三阶段写作流程',
'## 执行步骤（顺序执行）\n### Step 1: 大纲（writer）\n- 与用户确认受众、目的与篇幅，输出章节大纲；等待用户确认后进入下一步；输出：大纲\n### Step 2: 初稿（writer）\n- 按确认的大纲逐节成文，数据缺失处留占位符；输出：初稿笔记 draft-{标题}.md\n### Step 3: 润色（writer）\n- 优化结构与表达，标注主要修改点，输出终稿 final-{标题}.md',
'## 异常处理\n- 用户否定大纲 → 回到 Step 1 重写，最多 3 轮后请用户提供参考范文',
2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_pipeline WHERE scope = 'PLATFORM' AND pipeline_code = 'doc-writing');

INSERT INTO agent_pipeline (scope, tenant_id, pipeline_code, pipeline_name, description, steps, exception_handling, order_num)
SELECT 'PLATFORM', 0, 'incident-diagnosis', '故障排查流水线',
'现象 → 排查 → 结论 → 建议 的标准化运维排障流程',
'## 执行步骤（顺序执行）\n### Step 1: 现象确认（ops）\n- 向用户确认故障现象、发生时间、影响范围；输出：现象摘要\n### Step 2: 只读排查（ops）\n- 仅执行只读命令（日志、状态、监控）定位原因，禁止任何变更操作；输出：排查记录与疑似根因\n### Step 3: 结论与建议（ops）\n- 按"现象→排查→根因→修复建议"输出结论；修复动作需用户确认后执行；输出：结论报告笔记',
'## 异常处理\n- 排查需要变更操作 → 必须走用户确认（HITL），批准后执行并记录',
3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_pipeline WHERE scope = 'PLATFORM' AND pipeline_code = 'incident-diagnosis');

-- ============================================================
-- 内置提示词模板种子数据
-- ============================================================
INSERT INTO prompt_template (scope, tenant_id, template_code, template_name, category, content, order_num)
SELECT 'PLATFORM', 0, 'daily-brief', '每日工作简报', '汇报',
'请根据以下信息生成今日工作简报。\n## 输入\n- 日期: {{date}}\n- 完成事项: {{doneItems}}\n- 进行中: {{ongoingItems}}\n- 阻塞问题: {{blockers}}\n## 简报结构\n1. 今日概览（一句话）\n2. 已完成（分条）\n3. 进行中与风险（标注优先级）\n4. 明日计划（不超过 5 条）\n## 输出要求：简洁，总字数 300 字以内，适合快速阅读',
1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scope = 'PLATFORM' AND template_code = 'daily-brief');

INSERT INTO prompt_template (scope, tenant_id, template_code, template_name, category, content, order_num)
SELECT 'PLATFORM', 0, 'weekly-report', '周报生成', '汇报',
'请根据本周工作记录生成周报。\n## 输入\n- 本周日期范围: {{dateRange}}\n- 工作记录: {{workLogs}}\n## 结构\n1. 本周核心成果（量化优先）\n2. 重点项目进展（含状态：进行中/已完成/延期）\n3. 问题与风险（附建议）\n4. 下周计划（按优先级排序）\n## 输出要求：结论先行，数据说话，避免流水账',
2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scope = 'PLATFORM' AND template_code = 'weekly-report');

INSERT INTO prompt_template (scope, tenant_id, template_code, template_name, category, content, order_num)
SELECT 'PLATFORM', 0, 'meeting-notes', '会议纪要整理', '办公',
'请把以下会议内容整理成结构化纪要。\n## 输入\n- 会议主题: {{topic}}\n- 原始记录: {{rawNotes}}\n## 输出结构\n1. 基本信息（时间/参会人/主题）\n2. 议题与讨论要点（分议题列出）\n3. 决议事项（加粗）\n4. 待办清单（负责人 + 截止时间，表格呈现）\n## 要求：不遗漏行动项，不添加会议未提及内容',
3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scope = 'PLATFORM' AND template_code = 'meeting-notes');

INSERT INTO prompt_template (scope, tenant_id, template_code, template_name, category, content, order_num)
SELECT 'PLATFORM', 0, 'title-gen', '标题生成（多风格）', '写作',
'请为主题生成标题候选。\n## 输入\n- 主题/内容摘要: {{content}}\n- 平台: {{platform}}\n- 风格: {{style}}（可选：吸睛/正式/悬念/数据型）\n## 输出：{{count}} 个候选标题，每个附一句适用说明，最后标注推荐项及理由',
4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scope = 'PLATFORM' AND template_code = 'title-gen');

INSERT INTO prompt_template (scope, tenant_id, template_code, template_name, category, content, order_num)
SELECT 'PLATFORM', 0, 'incident-brief', '故障通报模板', '运维',
'请根据故障处理记录生成对外通报。\n## 输入\n- 故障现象: {{symptom}}\n- 影响范围: {{impact}}\n- 时间线: {{timeline}}\n- 根因与措施: {{rootCause}}\n## 输出结构\n1. 故障概述（一句话）\n2. 影响面与时长（量化）\n3. 处理时间线（表格）\n4. 根因说明（非技术语言版本）\n5. 改进措施与责任跟进',
5
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE scope = 'PLATFORM' AND template_code = 'incident-brief');
