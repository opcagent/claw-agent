-- ============================================================
-- V48：用户渠道绑定表（支持单聊 + 群聊场景）
--   1. sys_user_channel：用户与渠道（微信/Slack/Telegram 等）的绑定关系
--   2. 支持单聊（channel_group_id 为 NULL）和群聊（channel_group_id 有值）
--   3. OAuth token 加密存储，支持 token 刷新
-- 规约：脚本只增不改；幂等插入（NOT EXISTS 防重）
-- ============================================================

-- ------------------------------------------------------------
-- 1. 用户渠道绑定表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_channel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id VARCHAR(64) NOT NULL COMMENT '平台用户 ID（关联 sys_user.id）',
    channel_type VARCHAR(32) NOT NULL COMMENT '渠道类型：wechat / slack / telegram / web',
    channel_user_id VARCHAR(128) NOT NULL COMMENT '渠道侧用户标识（如微信 openid、Slack user_id）',
    channel_username VARCHAR(128) DEFAULT NULL COMMENT '渠道侧显示名（如微信昵称）',
    channel_group_id VARCHAR(128) DEFAULT NULL COMMENT '群组 ID（单聊为 NULL，群聊必填）',
    channel_group_name VARCHAR(256) DEFAULT NULL COMMENT '群组名称（如微信群名、Slack channel 名）',
    group_role VARCHAR(32) DEFAULT 'member' COMMENT '群内角色：owner / admin / member',
    access_token TEXT DEFAULT NULL COMMENT 'OAuth access_token（加密存储）',
    refresh_token TEXT DEFAULT NULL COMMENT 'OAuth refresh_token（加密存储）',
    status TINYINT DEFAULT 1 COMMENT '状态：1 启用 / 0 禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    creator VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    updater VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    creator_id VARCHAR(64) DEFAULT NULL COMMENT '创建人 ID',
    updater_id VARCHAR(64) DEFAULT NULL COMMENT '更新人 ID',
    UNIQUE KEY uk_user_channel_group (user_id, channel_type, channel_group_id),
    KEY idx_channel_user (channel_type, channel_user_id),
    KEY idx_channel_group (channel_type, channel_group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户渠道绑定表';

-- ------------------------------------------------------------
-- 2. 渠道类型字典（前端下拉选择用）
-- ------------------------------------------------------------
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'channel_type', '微信', 'wechat', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'channel_type' AND dict_value = 'wechat');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'channel_type', 'Slack', 'slack', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'channel_type' AND dict_value = 'slack');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'channel_type', 'Telegram', 'telegram', 3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'channel_type' AND dict_value = 'telegram');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'channel_type', 'Web', 'web', 4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'channel_type' AND dict_value = 'web');

-- ------------------------------------------------------------
-- 3. 群角色字典
-- ------------------------------------------------------------
INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'group_role', '群主', 'owner', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'group_role' AND dict_value = 'owner');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'group_role', '管理员', 'admin', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'group_role' AND dict_value = 'admin');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort)
SELECT 0, 'group_role', '成员', 'member', 3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = 0 AND dict_type = 'group_role' AND dict_value = 'member');

-- ------------------------------------------------------------
-- 4. 渠道管理菜单（挂在「系统管理」下）
-- ------------------------------------------------------------
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, icon, menu_type, visible, status, perms)
SELECT '渠道管理', id, 20, '/system/channels', 'link', 'C', 1, 1, 'system:channel:list'
FROM sys_menu
WHERE menu_name = '系统管理' AND menu_type = 'M'
LIMIT 1;

-- 渠道管理按钮权限（新增 / 编辑 / 删除）
INSERT INTO sys_menu (menu_name, parent_id, order_num, perms, menu_type, visible, status)
SELECT '渠道新增', m.id, 1, 'system:channel:add', 'F', 1, 1
FROM sys_menu m WHERE m.menu_name = '渠道管理' AND m.menu_type = 'C' LIMIT 1;

INSERT INTO sys_menu (menu_name, parent_id, order_num, perms, menu_type, visible, status)
SELECT '渠道编辑', m.id, 2, 'system:channel:edit', 'F', 1, 1
FROM sys_menu m WHERE m.menu_name = '渠道管理' AND m.menu_type = 'C' LIMIT 1;

INSERT INTO sys_menu (menu_name, parent_id, order_num, perms, menu_type, visible, status)
SELECT '渠道删除', m.id, 3, 'system:channel:remove', 'F', 1, 1
FROM sys_menu m WHERE m.menu_name = '渠道管理' AND m.menu_type = 'C' LIMIT 1;

-- 角色授权：三个内置角色均可访问渠道管理菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r, sys_menu m
WHERE m.menu_name IN ('渠道管理', '渠道新增', '渠道编辑', '渠道删除')
  AND m.perms IS NOT NULL
  AND r.id IN (1, 2, 3)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
