-- ============================================================
-- V16__preset_top_level_menu.sql
-- Flyway 迁移脚本：预设模板提升为一级菜单（目录）
--   背景：预设模板（102）原挂在「智能对话」目录下作为二级菜单，
--   按产品要求提升为独立一级目录。前端导航中 M 型目录无子菜单时
--   点击直达自身 path，故直接改类型即可，无需新建子菜单。
--   菜单顺序调整为：智能对话(1) → 预设模板(2) → 系统管理(3) → Agent 配置(4)
--   角色授权关系（sys_role_menu）不受影响：持有 102 的角色仍可见
-- ============================================================

-- 1. 预设模板：二级菜单 → 一级目录（parent_id 归根、路径直达页面）
UPDATE sys_menu
SET menu_type = 'M',
    parent_id = 0,
    order_num = 2,
    path      = '/presets',
    icon      = 'sparkles'
WHERE id = 102;

-- 2. 后续目录顺延排序（智能对话保持 1）
UPDATE sys_menu SET order_num = 3 WHERE id = 2;
UPDATE sys_menu SET order_num = 4 WHERE id = 3;
