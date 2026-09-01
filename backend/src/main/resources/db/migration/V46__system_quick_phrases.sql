-- ------------------------------------------------------------
-- V46：平台预设快捷指令（系统级，所有用户可见）
-- user_id = 'SYSTEM' 的指令作为平台内置模板，
-- 后端查询时 UNION 到每个用户的指令列表前面。
-- ------------------------------------------------------------

INSERT INTO user_quick_phrase (tenant_id, user_id, username, title, content, sort_order)
SELECT 0, 'SYSTEM', '系统', '联网搜索',
'请帮我搜索关于「」的最新信息，要求：\n1. 使用 web_search 工具搜索\n2. 至少搜索 3 个不同关键词组合\n3. 整理为表格（标题、链接、摘要）\n4. 标注信息来源和时间',
1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_quick_phrase WHERE user_id = 'SYSTEM' AND title = '联网搜索');

INSERT INTO user_quick_phrase (tenant_id, user_id, username, title, content, sort_order)
SELECT 0, 'SYSTEM', '系统', '翻译为英文',
'请将以下内容翻译为地道的英文，要求：\n1. 保持原文语义完整\n2. 使用目标语言的自然表达，避免直译\n3. 专业术语首次出现时附中文注释\n4. 如有多种译法，给出推荐译法并说明差异\n\n待翻译内容：',
2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_quick_phrase WHERE user_id = 'SYSTEM' AND title = '翻译为英文');

INSERT INTO user_quick_phrase (tenant_id, user_id, username, title, content, sort_order)
SELECT 0, 'SYSTEM', '系统', '总结要点',
'请帮我总结以下内容的核心要点：\n1. 用 3-5 条要点概括\n2. 每条要点一句话，突出关键信息\n3. 如有数据或结论，优先提取\n4. 最后给出一句话总结\n\n内容如下：',
3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_quick_phrase WHERE user_id = 'SYSTEM' AND title = '总结要点');

INSERT INTO user_quick_phrase (tenant_id, user_id, username, title, content, sort_order)
SELECT 0, 'SYSTEM', '系统', '写商务邮件',
'请帮我撰写一封商务邮件：\n- 收件人：\n- 目的：\n- 关键信息：\n- 语气：专业/友好\n\n要求：\n1. 开头简洁，直入主题\n2. 正文条理清晰\n3. 结尾有明确的行动号召（CTA）\n4. 中英文各一版',
4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_quick_phrase WHERE user_id = 'SYSTEM' AND title = '写商务邮件');

INSERT INTO user_quick_phrase (tenant_id, user_id, username, title, content, sort_order)
SELECT 0, 'SYSTEM', '系统', '数据分析',
'请帮我分析以下数据/信息：\n1. 提取关键指标和趋势\n2. 找出异常值或值得关注的点\n3. 用表格呈现核心数据对比\n4. 给出 2-3 条可操作的建议\n\n数据/信息：',
5
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_quick_phrase WHERE user_id = 'SYSTEM' AND title = '数据分析');

INSERT INTO user_quick_phrase (tenant_id, user_id, username, title, content, sort_order)
SELECT 0, 'SYSTEM', '系统', '生成调研报告',
'请针对「」主题进行深度调研并输出报告：\n1. 使用 web_search 搜索至少 5 个来源\n2. 报告结构：背景 → 现状 → 关键发现 → 对比分析 → 建议\n3. 数据用表格呈现，观点附来源链接\n4. 将报告保存为笔记',
6
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_quick_phrase WHERE user_id = 'SYSTEM' AND title = '生成调研报告');

INSERT INTO user_quick_phrase (tenant_id, user_id, username, title, content, sort_order)
SELECT 0, 'SYSTEM', '系统', '代码审查',
'请审查以下代码，关注：\n1. 潜在的 Bug 和安全风险\n2. 性能问题\n3. 代码风格与可读性\n4. 给出修改建议（附修改后代码）\n\n```（粘贴代码）\n```',
7
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_quick_phrase WHERE user_id = 'SYSTEM' AND title = '代码审查');

INSERT INTO user_quick_phrase (tenant_id, user_id, username, title, content, sort_order)
SELECT 0, 'SYSTEM', '系统', '头脑风暴',
'我想就「」进行头脑风暴，请：\n1. 从至少 5 个不同角度提出想法\n2. 每个角度给 2-3 个具体方案\n3. 标注每个方案的可行性（高/中/低）\n4. 推荐最值得尝试的 1-2 个方向并说明理由',
8
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_quick_phrase WHERE user_id = 'SYSTEM' AND title = '头脑风暴');

INSERT INTO user_quick_phrase (tenant_id, user_id, username, title, content, sort_order)
SELECT 0, 'SYSTEM', '系统', '制定计划',
'请帮我制定一个关于「」的执行计划：\n1. 明确目标和关键里程碑\n2. 拆解为可执行的步骤（每步有明确产出）\n3. 估算每步所需时间\n4. 标注依赖关系和风险点\n5. 用甘特图式表格呈现',
9
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_quick_phrase WHERE user_id = 'SYSTEM' AND title = '制定计划');

INSERT INTO user_quick_phrase (tenant_id, user_id, username, title, content, sort_order)
SELECT 0, 'SYSTEM', '系统', '对比分析',
'请帮我对比分析以下选项：「」\n1. 列出每个选项的优势和劣势\n2. 从成本、效率、风险、可扩展性四个维度打分\n3. 用表格呈现对比结果\n4. 给出推荐选择及理由',
10
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_quick_phrase WHERE user_id = 'SYSTEM' AND title = '对比分析');
